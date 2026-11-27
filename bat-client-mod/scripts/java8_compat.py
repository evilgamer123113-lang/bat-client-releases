"""Lower Java 9-17 syntax and stdlib calls to pure Java 8 (--release 8) source."""
import re


def _find_matching(text: str, start: int, open_ch: str, close_ch: str) -> int:
    depth = 0
    i = start
    n = len(text)
    in_str = None
    in_line_comment = False
    in_block_comment = False
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ''
        if in_line_comment:
            if c == '\n':
                in_line_comment = False
        elif in_block_comment:
            if c == '*' and nxt == '/':
                in_block_comment = False
                i += 1
        elif in_str:
            if c == '\\':
                i += 1
            elif c == in_str:
                in_str = None
        else:
            if c == '/' and nxt == '/':
                in_line_comment = True
                i += 1
            elif c == '/' and nxt == '*':
                in_block_comment = True
                i += 1
            elif c in ('"', "'"):
                in_str = c
            elif c == open_ch:
                depth += 1
            elif c == close_ch:
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    return -1


def _split_top_level(text: str, sep: str = ',') -> list[str]:
    parts = []
    cur = []
    depth = 0
    in_str = None
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if in_str:
            cur.append(c)
            if c == '\\' and i + 1 < n:
                cur.append(text[i + 1])
                i += 1
            elif c == in_str:
                in_str = None
        else:
            if c in ('"', "'"):
                in_str = c
                cur.append(c)
            elif c in '(<[{':
                depth += 1
                cur.append(c)
            elif c in ')>]}':
                depth -= 1
                cur.append(c)
            elif c == sep and depth == 0:
                s = ''.join(cur).strip()
                if s:
                    parts.append(s)
                cur = []
            else:
                cur.append(c)
        i += 1
    s = ''.join(cur).strip()
    if s:
        parts.append(s)
    return parts


def _convert_text_blocks(text: str) -> str:
    def repl(m):
        body = m.group(1)
        lines = body.splitlines()
        non_empty = [ln for ln in lines if ln.strip()]
        indent = min((len(ln) - len(ln.lstrip(' \t')) for ln in non_empty), default=0)
        trimmed = [ln[indent:] if len(ln) >= indent else '' for ln in lines]
        joined = '\\n'.join(ln.replace('\\', '\\\\').replace('"', '\\"') for ln in trimmed)
        return '"' + joined + '"'
    return re.sub(r'"""\r?\n(.*?)"""', repl, text, flags=re.S)


def _convert_records(text: str) -> str:
    pat = re.compile(r'(?m)^([ \t]*)(?:(public|private|protected)\s+)?(?:(static)\s+)?record\s+([A-Za-z_]\w*(?:<[^>]+>)?)\s*\(')
    while True:
        m = pat.search(text)
        if not m:
            break
        indent, vis, st, name_gen = m.group(1), m.group(2) or '', m.group(3) or '', m.group(4)
        raw_name = name_gen.split('<', 1)[0]
        paren_start = m.end() - 1
        paren_end = _find_matching(text, paren_start, '(', ')')
        if paren_end < 0:
            break
        params_str = text[paren_start + 1:paren_end]
        brace_start = text.find('{', paren_end + 1)
        if brace_start < 0:
            break
        between = text[paren_end + 1:brace_start].strip()
        brace_end = _find_matching(text, brace_start, '{', '}')
        if brace_end < 0:
            break
        body = text[brace_start + 1:brace_end]

        params = _split_top_level(params_str)
        fields = []
        for p in params:
            p = re.sub(r'@\w+(?:\([^)]*\))?\s*', '', p).strip()
            idx = p.rfind(' ')
            ftype, fname = p[:idx].strip(), p[idx + 1:].strip()
            fields.append((ftype, fname))

        # Handle compact constructor: `public Name { ... }`
        compact_pat = re.compile(r'\b(?:public|private|protected)?\s*' + re.escape(raw_name) + r'\s*\{')
        cm = compact_pat.search(body)
        compact_body = ''
        if cm:
            cb_start = cm.end() - 1
            cb_end = _find_matching(body, cb_start, '{', '}')
            if cb_end > cb_start:
                compact_body = body[cb_start + 1:cb_end]
                body = body[:cm.start()] + body[cb_end + 1:]

        mods = ' '.join(x for x in (vis, st or ('static' if indent else ''), 'final') if x)
        out = [f'{indent}{mods} class {name_gen} {between} {{' if between else f'{indent}{mods} class {name_gen} {{']
        for ft, fn in fields:
            out.append(f'{indent}    private final {ft} {fn};')
        ctor_params = ', '.join(f'{ft} {fn}' for ft, fn in fields)
        out.append(f'{indent}    public {raw_name}({ctor_params}) {{')
        if compact_body:
            out.append(compact_body)
        for _, fn in fields:
            out.append(f'{indent}        this.{fn} = {fn};')
        out.append(f'{indent}    }}')
        for ft, fn in fields:
            out.append(f'{indent}    public {ft} {fn}() {{ return this.{fn}; }}')
        if fields:
            eq_checks = ' && '.join(
                f'this.{fn} == o.{fn}' if ft in ('int', 'long', 'float', 'double', 'boolean', 'byte', 'short', 'char')
                else f'java.util.Objects.equals(this.{fn}, o.{fn})'
                for ft, fn in fields
            )
            hash_args = ', '.join(fn for _, fn in fields)
            out.append(f'{indent}    @Override public boolean equals(Object obj) {{ if (this == obj) return true; if (!(obj instanceof {raw_name})) return false; {raw_name} o = ({raw_name}) obj; return {eq_checks}; }}')
            out.append(f'{indent}    @Override public int hashCode() {{ return java.util.Objects.hash({hash_args}); }}')
        out.append(body)
        out.append(f'{indent}}}')
        text = text[:m.start()] + '\n'.join(out) + text[brace_end + 1:]
    return text


def _strip_comments_preserve_strings(text: str) -> str:
    out = []
    i = 0
    n = len(text)
    in_str = None
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ''
        if in_str:
            out.append(c)
            if c == '\\' and i + 1 < n:
                out.append(nxt)
                i += 2
                continue
            if c == in_str:
                in_str = None
            i += 1
        elif c in ('"', "'"):
            in_str = c
            out.append(c)
            i += 1
        elif c == '/' and nxt == '/':
            while i < n and text[i] != '\n':
                i += 1
        elif c == '/' and nxt == '*':
            i += 2
            while i + 1 < n and not (text[i] == '*' and text[i + 1] == '/'):
                i += 1
            i += 2
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def _convert_switch_arrows(text: str) -> str:
    # Handle switch expressions and switch statements with `case X -> ...` / `default -> ...`
    pat = re.compile(r'\bswitch\s*\(')
    pos = 0
    while True:
        m = pat.search(text, pos)
        if not m:
            break
        p_end = _find_matching(text, m.end() - 1, '(', ')')
        if p_end < 0:
            break
        b_start = p_end + 1
        while b_start < len(text) and text[b_start].isspace():
            b_start += 1
        if b_start >= len(text) or text[b_start] != '{':
            pos = m.end()
            continue
        b_end = _find_matching(text, b_start, '{', '}')
        if b_end < 0:
            break
        block = text[b_start + 1:b_end]
        block_clean = _strip_comments_preserve_strings(block)
        if '->' not in block_clean:
            pos = b_end + 1
            continue
        block = block_clean

        # Check if switch is used as an expression (preceded by `=`, `return`, `(`, `,`, `:`)
        pre = text[:m.start()].rstrip()
        is_expr = bool(pre) and (pre[-1] in ('=', '(', ',', ':') or pre.endswith('return'))

        # Parse arms inside block
        arms = []
        i = 0
        n = len(block)
        while i < n:
            while i < n and block[i].isspace():
                i += 1
            if i >= n:
                break
            arrow = block.find('->', i)
            if arrow < 0:
                break
            label = block[i:arrow].strip()
            j = arrow + 2
            while j < n and block[j].isspace():
                j += 1
            if j < n and block[j] == '{':
                end_arm = _find_matching(block, j, '{', '}')
                rhs = block[j + 1:end_arm].strip()
                i = end_arm + 1
                is_block = True
            else:
                # Scan until `;` at depth 0
                d = 0
                in_s = None
                k = j
                while k < n:
                    ch = block[k]
                    if in_s:
                        if ch == '\\':
                            k += 1
                        elif ch == in_s:
                            in_s = None
                    else:
                        if ch in ('"', "'"):
                            in_s = ch
                        elif ch in '([{':
                            d += 1
                        elif ch in ')]}':
                            d -= 1
                        elif ch == ';' and d == 0:
                            break
                    k += 1
                rhs = block[j:k].strip()
                i = k + 1
                is_block = False
            arms.append((label, rhs, is_block))

        if not arms:
            pos = b_end + 1
            continue

        def emit_labels(lbl: str) -> str:
            if lbl.startswith('default'):
                return 'default:'
            rest = lbl[len('case'):].strip()
            return ' '.join(f'case {item.strip()}:' for item in _split_top_level(rest, ','))

        if is_expr:
            # Determine if we are in `return switch (...)` vs `Type x = switch (...)`
            if pre.endswith('return'):
                cases_str = []
                for lbl, rhs, is_b in arms:
                    lstr = emit_labels(lbl)
                    if is_b:
                        r = rhs.replace('yield ', 'return ')
                        cases_str.append(f'{lstr} {{ {r} }}')
                    elif rhs.startswith('throw '):
                        cases_str.append(f'{lstr} {rhs};')
                    else:
                        cases_str.append(f'{lstr} return {rhs};')
                repl = 'switch (' + text[m.end():p_end] + ') { ' + ' '.join(cases_str) + ' }'
                ret_idx = text.rfind('return', 0, m.start())
                text = text[:ret_idx] + repl + text[b_end + 1:]
                # strip trailing semicolon if present
                after = text[ret_idx + len(repl):].lstrip()
                if after.startswith(';'):
                    semi = text.find(';', ret_idx + len(repl))
                    text = text[:semi] + text[semi + 1:]
                pos = ret_idx + len(repl)
                continue
            else:
                # Wrap in an inline helper supplier or evaluate via ternary if simple, else Supplier
                cases_str = []
                for lbl, rhs, is_b in arms:
                    lstr = emit_labels(lbl)
                    if is_b:
                        r = rhs.replace('yield ', 'return ')
                        cases_str.append(f'{lstr} {{ {r} }}')
                    elif rhs.startswith('throw '):
                        cases_str.append(f'{lstr} {rhs};')
                    else:
                        cases_str.append(f'{lstr} return {rhs};')
                # Use a generic lambda helper J8.eval(key, _sw -> { switch (_sw) { ... } })
                has_default = any(lbl.startswith('default') for lbl, _, _ in arms)
                tail = '' if has_default else ' throw new IllegalStateException();'
                sw_key = text[m.end():p_end]
                repl = 'com.batclient.platform.java8.J8.eval(' + sw_key + ', _sw -> { switch (_sw) { ' + ' '.join(cases_str) + ' }' + tail + ' })'
                text = text[:m.start()] + repl + text[b_end + 1:]
                pos = m.start() + len(repl)
                continue
        else:
            cases_str = []
            for lbl, rhs, is_b in arms:
                lstr = emit_labels(lbl)
                if is_b:
                    cases_str.append(f'{lstr} {{ {rhs} }} break;')
                elif rhs.startswith(('return ', 'throw ', 'break', 'continue')):
                    cases_str.append(f'{lstr} {rhs};')
                else:
                    cases_str.append(f'{lstr} {rhs}; break;')
            repl = '{ ' + ' '.join(cases_str) + ' }'
            text = text[:b_start] + repl + text[b_end + 1:]
            pos = b_start + len(repl)
    return text


def _extract_lhs_expr(text: str, end_pos: int) -> tuple[int, str]:
    i = end_pos - 1
    while i >= 0 and text[i].isspace():
        i -= 1
    end_trimmed = i + 1
    d = 0
    while i >= 0:
        ch = text[i]
        if ch in ')]':
            d += 1
        elif ch in '([':
            if d == 0:
                break
            d -= 1
        elif d == 0 and not (ch.isalnum() or ch in '_$.'):
            break
        i -= 1
    return i + 1, text[i + 1:end_trimmed]


def _convert_instanceof_patterns(text: str) -> str:
    pat = re.compile(r'\s+instanceof\s+([A-Za-z_][\w.<>\[\]]*(?:\s*<\?>)?)\s+([a-z_]\w*)\b')
    pos = 0
    while True:
        m = pat.search(text, pos)
        if not m:
            break
        typ, var = m.group(1), m.group(2)
        if var in ('if', 'else', 'return', 'throw', 'new', 'null', 'true', 'false'):
            pos = m.end()
            continue
        expr_start, expr = _extract_lhs_expr(text, m.start())
        if not expr:
            pos = m.end()
            continue
        search_start = max(0, expr_start - 200)
        if_matches = list(re.finditer(r'\bif\s*\(', text[search_start:expr_start]))
        if if_matches:
            if_m = if_matches[-1]
            if_paren = search_start + if_m.end() - 1
            if_end = _find_matching(text, if_paren, '(', ')')
            if if_end >= m.end():
                cond_rest = re.sub(r'(?<!\.)\b' + re.escape(var) + r'\b', f'(({typ}) (Object) ({expr}))', text[m.end():if_end])
                after_if = text[if_end + 1:].lstrip()
                if after_if.startswith('{'):
                    brace_pos = text.find('{', if_end + 1)
                    neg = '!' in text[if_paren:expr_start]
                    close_brace = _find_matching(text, brace_pos, '{', '}')
                    inner = text[brace_pos + 1:close_brace] if close_brace > brace_pos else ''
                    if neg and re.search(r'\b(return|continue|throw)\b', inner) and close_brace > brace_pos:
                        text = (
                            text[:m.start()]
                            + f' instanceof {typ}'
                            + cond_rest
                            + text[if_end:close_brace + 1]
                            + f' {typ} {var} = ({typ}) (Object) ({expr});'
                            + text[close_brace + 1:]
                        )
                    else:
                        text = (
                            text[:m.start()]
                            + f' instanceof {typ}'
                            + cond_rest
                            + text[if_end:brace_pos + 1]
                            + f' {typ} {var} = ({typ}) (Object) ({expr});'
                            + text[brace_pos + 1:]
                        )
                    continue
                neg = '!' in text[if_paren:expr_start]
                semi_pos = text.find(';', if_end + 1)
                if neg and semi_pos > if_end:
                    stmt = text[if_end + 1:semi_pos + 1]
                    if re.search(r'\b(return|continue|throw)\b', stmt):
                        text = (
                            text[:m.start()]
                            + f' instanceof {typ}'
                            + cond_rest
                            + text[if_end:semi_pos + 1]
                            + f' {typ} {var} = ({typ}) (Object) ({expr});'
                            + text[semi_pos + 1:]
                        )
                        continue
                if semi_pos > if_end:
                    stmt = text[if_end + 1:semi_pos + 1]
                    text = (
                        text[:m.start()]
                        + f' instanceof {typ}'
                        + cond_rest
                        + text[if_end]
                        + f' {{ {typ} {var} = ({typ}) (Object) ({expr}); {stmt.strip()} }}'
                        + text[semi_pos + 1:]
                    )
                    continue
        line_end = text.find('\n', m.end())
        if line_end < 0:
            line_end = len(text)
        next_inst = text.find(' instanceof ', m.end(), line_end)
        stop = next_inst if next_inst > 0 else line_end
        rest = re.sub(r'(?<!\.)\b' + re.escape(var) + r'\b', f'(({typ}) (Object) ({expr}))', text[m.end():stop])
        text = text[:m.start()] + f' instanceof {typ}' + rest + text[stop:]
    return text


def _replace_receiver_call(text: str, method: str, helper_prefix: str, has_args: bool) -> str:
    needle = '.' + method + '('
    pos = 0
    while True:
        idx = text.find(needle, pos)
        if idx < 0:
            break
        # Walk backwards from idx to find the receiver expression
        i = idx - 1
        d = 0
        while i >= 0:
            ch = text[i]
            if ch in ')]':
                d += 1
            elif ch in '([':
                if d == 0:
                    break
                d -= 1
            elif d == 0 and not (ch.isalnum() or ch in '_$.'):
                break
            i -= 1
        recv_start = i + 1
        recv = text[recv_start:idx]
        if not recv or (method == 'readAllBytes' and recv in ('Files', 'java.nio.file.Files')):
            pos = idx + len(needle)
            continue
        paren_end = _find_matching(text, idx + len(needle) - 1, '(', ')')
        if paren_end < 0:
            break
        args = text[idx + len(needle):paren_end].strip()
        if has_args and args:
            repl = f'{helper_prefix}({recv}, {args})'
        else:
            repl = f'{helper_prefix}({recv})'
        text = text[:recv_start] + repl + text[paren_end + 1:]
        pos = recv_start + len(repl)
    return text


def lower_to_java8(text: str) -> str:
    text = _convert_text_blocks(text)
    text = _convert_records(text)
    text = _convert_switch_arrows(text)
    text = _convert_instanceof_patterns(text)
    text = text.replace('try (image) {', 'try (net.minecraft.client.texture.NativeImage _img = image) {')

    # Stdlib replacements -> com.batclient.platform.java8.J8
    if 'class LauncherAccess' in text and 'ProcessHandle' in text:
        text = re.sub(
            r'if\s*\(!ProcessHandle\.current\(\).*?throw new IllegalStateException',
            'if (!com.batclient.platform.java8.J8.isBatLauncherJ8(LAUNCHER_PID)) {\n            throw new IllegalStateException',
            text,
            flags=re.S,
        )
        text = re.sub(r'\n\s*private static boolean isBatLauncher\(ProcessHandle parent\).*', '\n}', text, flags=re.S)

    text = text.replace('import java.net.http.*;', 'import com.batclient.platform.java8.J8.HttpClient;\nimport com.batclient.platform.java8.J8.HttpRequest;\nimport com.batclient.platform.java8.J8.HttpResponse;')
    text = text.replace('import java.net.http.HttpClient;', 'import com.batclient.platform.java8.J8.HttpClient;')
    text = text.replace('import java.net.http.HttpRequest;', 'import com.batclient.platform.java8.J8.HttpRequest;')
    text = text.replace('import java.net.http.HttpResponse;', 'import com.batclient.platform.java8.J8.HttpResponse;')
    text = text.replace('java.net.http.HttpClient', 'com.batclient.platform.java8.J8.HttpClient')
    text = text.replace('java.net.http.HttpRequest', 'com.batclient.platform.java8.J8.HttpRequest')
    text = text.replace('java.net.http.HttpResponse', 'com.batclient.platform.java8.J8.HttpResponse')

    for fqn_prefix in ('java.util.', 'java.nio.file.'):
        text = text.replace(fqn_prefix + 'List.of(', 'List.of(')
        text = text.replace(fqn_prefix + 'List.copyOf(', 'List.copyOf(')
        text = text.replace(fqn_prefix + 'Set.of(', 'Set.of(')
        text = text.replace(fqn_prefix + 'Set.copyOf(', 'Set.copyOf(')
        text = text.replace(fqn_prefix + 'Map.of(', 'Map.of(')
        text = text.replace(fqn_prefix + 'Map.copyOf(', 'Map.copyOf(')
        text = text.replace(fqn_prefix + 'Files.readString(', 'Files.readString(')
        text = text.replace(fqn_prefix + 'Files.writeString(', 'Files.writeString(')
        text = text.replace(fqn_prefix + 'Path.of(', 'Path.of(')

    text = re.sub(r'\bList\.of\(', 'com.batclient.platform.java8.J8.listOf(', text)
    text = re.sub(r'\bList\.copyOf\(', 'com.batclient.platform.java8.J8.listCopyOf(', text)
    text = re.sub(r'\bSet\.of\(', 'com.batclient.platform.java8.J8.setOf(', text)
    text = re.sub(r'\bSet\.copyOf\(', 'com.batclient.platform.java8.J8.setCopyOf(', text)
    text = re.sub(r'\bMap\.of\(', 'com.batclient.platform.java8.J8.mapOf(', text)
    text = re.sub(r'\bMap\.copyOf\(', 'com.batclient.platform.java8.J8.mapCopyOf(', text)
    text = re.sub(r'\b(?:java\.util\.)?Objects\.requireNonNullElse\(', 'com.batclient.platform.java8.J8.requireNonNullElse(', text)
    text = re.sub(r'\bPath\.of\(', 'java.nio.file.Paths.get(', text)
    text = text.replace('.toList()', '.collect(java.util.stream.Collectors.toList())')
    text = text.replace('.orElseThrow()', '.orElseThrow(java.util.NoSuchElementException::new)')
    text = re.sub(r'\.toArray\(([A-Za-z0-9_.$]+)\[\]::new\)', r'.toArray(new \1[0])', text)
    text = text.replace('.strip()', '.trim()')
    text = re.sub(r'\bFiles\.readString\(', 'com.batclient.platform.java8.J8.readString(', text)
    text = re.sub(r'\bFiles\.writeString\(', 'com.batclient.platform.java8.J8.writeString(', text)
    text = re.sub(r'\bURLDecoder\.decode\(([^,]+),\s*StandardCharsets\.UTF_8\)', r'com.batclient.platform.java8.J8.urlDecode(\1)', text)
    text = re.sub(r'\bURLEncoder\.encode\(([^,]+),\s*StandardCharsets\.UTF_8\)', r'com.batclient.platform.java8.J8.urlEncode(\1)', text)
    text = _replace_receiver_call(text, 'isBlank', 'com.batclient.platform.java8.J8.isBlank', False)
    text = _replace_receiver_call(text, 'readNBytes', 'com.batclient.platform.java8.J8.readNBytes', True)
    text = _replace_receiver_call(text, 'readAllBytes', 'com.batclient.platform.java8.J8.readAllBytes', False)
    text = re.sub(r'("[^"\\]*(?:\\.[^"\\]*)*")\.formatted\(', r'String.format(\1, ', text)
    return text

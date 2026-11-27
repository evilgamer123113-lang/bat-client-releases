const { parentPort, workerData } = require('worker_threads');
const fs = require('fs');
const gracefulFs = require('graceful-fs');

gracefulFs.gracefulify(fs);

const { Client } = require('minecraft-launcher-core');
const launcher = new Client();
const { launchArgs } = workerData;

launcher.startMinecraft = function (launchArguments) {
    const child = require('child_process');
    const mc = child.spawn(
        this.options.javaPath || 'java',
        launchArguments,
        {
            cwd: this.options.overrides.cwd || this.options.root,
            detached: false,
            windowsHide: true,
            shell: false,
            stdio: ['pipe', 'pipe', 'pipe']
        }
    );
    mc.stdout.on('data', (data) => this.emit('data', data.toString('utf-8')));
    mc.stderr.on('data', (data) => this.emit('data', data.toString('utf-8')));
    mc.on('close', (code) => this.emit('close', code));
    return mc;
};

launcher.on('debug', (e) => parentPort.postMessage({ type: 'debug', data: e }));
launcher.on('data', (e) => parentPort.postMessage({ type: 'data', data: e }));

launcher.on('progress', (e) => {
    const percentage = Math.round((e.task / e.total) * 100);
    parentPort.postMessage({ type: 'progress', percent: percentage, taskType: e.type });
});

async function run() {
    try {
        const minecraftProcess = await launcher.launch(launchArgs);
        
        if (!minecraftProcess) {
            parentPort.postMessage({ type: 'error', data: 'Engine handle returned null.' });
            return;
        }

        parentPort.postMessage({ type: 'running' });

        minecraftProcess.on('data', (data) => {
            parentPort.postMessage({ type: 'game-log', data: data.toString().trim() });
        });
        
        minecraftProcess.on('close', (code) => {
            parentPort.postMessage({ type: 'close', code });
        });

    } catch (err) {
        parentPort.postMessage({ type: 'error', data: err.stack || err.message });
    }
}

run();
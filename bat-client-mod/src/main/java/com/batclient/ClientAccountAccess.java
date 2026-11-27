package com.batclient;

import com.batclient.accounts.AccountSession;

public interface ClientAccountAccess {
    String bat$accounts(String action,String value);
    AccountSession bat$captureAccount();
    void bat$applyAccount(AccountSession account);
}

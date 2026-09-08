package com.ultron.agent;

interface IUserService {
    String executeCommand(String command);
    void destroy() = 16777114;
}


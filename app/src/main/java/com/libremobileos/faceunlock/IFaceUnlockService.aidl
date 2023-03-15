package com.libremobileos.faceunlock;

interface IFaceUnlockService {
    void enrollResult(int remaining);
    void error(int error);
    String getStorePath();
}

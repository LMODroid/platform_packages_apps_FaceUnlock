package com.libremobileos.facedetect;

interface IFaceDetectService {
    void enrollResult(int remaining);
    void error(int error);
}

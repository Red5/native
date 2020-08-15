#ifndef CPPWRAPPER_R5MPEG_H
#define CPPWRAPPER_R5MPEG_H

#include <jni.h>
#include <thread>
#include <iostream>
#include <vector>
#include <csignal>
#include <cstddef>
#include <cstring>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cmath>
#include <algorithm>
#include <atomic>
#include <chrono>

// phoboslabs mpeg decoder
#include "pl_mpeg.h"
// Unit-X mpeg-ts demuxer
#include "mpegts_demuxer.h"

#define TS_SYNC_BYTE 0x47
#define TYPE_AUDIO 0x0f
#define TYPE_VIDEO 0x1b

// all the fields needed to configure the handler
typedef struct config_t {
    // identifier for the instance (ex. stream name)
    const char* mpegName;
    // audio
    // sample-rate
	int sample_rate = 48000;
	// channels
	int no_channels = 1;
    // video
    int width = 640;
    int height = 480;
} config_t;

// global static reference for the JVM
static JavaVM *jvm;

/**
 * MPEG-TS handler implementation.
 */
class TSHandler {
    public:
        // pointer for ctx lookup of the instance
        uintptr_t selfId = 0;
        // configuration
        config_t *config;
        // plabs mpeg struct
        plm_t *plm;
        // receiver
        jobject receiver = nullptr;
        jclass receiverClass = nullptr;
        // start time
        std::chrono::_V2::steady_clock::time_point start_time;
        // last decode time
        std::chrono::_V2::steady_clock::time_point last_time;
        // MPEG-TS demuxer
        MpegTsDemuxer demuxer;
        // debug flag
        bool debug = false;

        TSHandler();

        virtual ~TSHandler() {
            try {
                plm_destroy(plm);
                // clean up jvm stuff
                if (receiver != nullptr) {
                    JNIEnv *env;
                    jvm->GetEnv((void **) &env, JNI_VERSION_1_8);
                    env->DeleteGlobalRef(receiver);
                    env->DeleteGlobalRef(receiverClass);
                }
            } catch(...) {};
            std::cout << "freed handler: " << selfId << std::endl;
        };

        void decodeVideo(std::vector<uint8_t> vbuf);

        void decodeAudio(std::vector<uint16_t> abuf);

        void recvData(uint8_t *data, size_t data_len);

        void recvData(uint16_t *data, size_t data_len);

        void onDemuxed(EsFrame *pEs);

};

// generic context handler instance types
class R5MpegContext {
    public:
        R5MpegContext() {};
 
        TSHandler* newHandler() {
            TSHandler *handler = new TSHandler();
            if (handler != 0) {
                handler->selfId = (uintptr_t) handler;
                return handler;
            }
            return 0;
        }

        TSHandler* getHandler(long id) {
            uintptr_t locate = (uintptr_t) id;
            TSHandler *handler = (TSHandler *) locate;
            if (handler != 0) {
                return handler;
            }
            return 0;
        }

        long removeHandler(long id) {
            uintptr_t locate = (uintptr_t) id;
            TSHandler *handler = (TSHandler *) locate;
            if (handler != 0) {
                delete handler;
            }
            return 0L;
        }

};

// without having this here, we'd get an undefined symbol
static R5MpegContext mpeg_ctx;

// Main calls to create a handler/receiver for hitting their primary methods
class R5MpegMain {
    public:
        R5MpegMain() {};
        // create an handler instance
        long create_handler();
        // initialize
        void init(void *handler_arg);
        // destroy the handler instance
        void destroy(long id);
};

static R5MpegMain maininator;

#endif //CPPWRAPPER_R5MPEG_H

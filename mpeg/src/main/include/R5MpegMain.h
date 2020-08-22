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
#include <any>
#include <algorithm>
#include <atomic>
#include <chrono>

// phoboslabs mpeg decoder
#include "pl_mpeg.h"
// Unit-X mpeg-ts mux/demux
#include "mpegts_demuxer.h"
#include "mpegts_muxer.h"

extern "C" {

#define TS_SYNC_BYTE 0x47

// AAC audio (15)
#define TYPE_AUDIO 0x0f
// h264 video (27)
#define TYPE_VIDEO 0x1b

// Audio PID
#define AUDIO_PID 257
// Video PID
#define VIDEO_PID 256
// PMT PID
#define PMT_PID 100

// fourCC - http://www.fourcc.org/codecs.php
const uint32_t TYPE_MP2A = (('M'<<24) | ('P'<<16) | ('2'<<8) | 'A');
const uint32_t TYPE_ADTS = (('A'<<24) | ('D'<<16) | ('T'<<8) | 'S');
const uint32_t TYPE_I420 = (('I'<<24) | ('4'<<16) | ('2'<<8) | '0');
const uint32_t TYPE_MP1V = (('M'<<24) | ('P'<<16) | ('1'<<8) | 'V'); // MPEG, MPG1, MP1V
const uint32_t TYPE_H264 = (('H'<<24) | ('2'<<16) | ('6'<<8) | '4');

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
    // mpeg-ts options
    uint16_t pmtPid = PMT_PID;
    uint16_t audioPid = 0;
    uint16_t videoPid = 0;
    uint16_t metaPid = 0;
    uint8_t streamId = 224; // 0xe0
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
        // MPEG-TS demuxer
        std::shared_ptr<MpegTsDemuxer> demuxer;
        // MPEG-TS muxer
        std::shared_ptr<MpegTsMuxer> muxer;
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

        bool init();

        void decodeVideo(std::vector<uint8_t> vbuf);

        void decodeAudio(std::vector<uint16_t> abuf);

        void recvData(uint8_t *data, size_t data_len);

        void recvData(uint8_t *data, size_t data_len, uint64_t pts, uint16_t pid);

        void recvData(uint16_t *data, size_t data_len);

        void onDemuxed(EsFrame *pEs);

        void onMuxed(SimpleBuffer &rTsOutBuffer);

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
        bool init(void *handler_arg);
        // destroy the handler instance
        void destroy(long id);
};

static R5MpegMain maininator;

}

#endif //CPPWRAPPER_R5MPEG_H

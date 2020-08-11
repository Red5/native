#ifndef CPPWRAPPER_NDI_H
#define CPPWRAPPER_NDI_H

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
#include <algorithm>
#include <atomic>

#ifdef _WIN32

#ifdef _WIN64
#pragma comment(lib, "Processing.NDI.Lib.x64.lib")
#else // _WIN64
#pragma comment(lib, "Processing.NDI.Lib.x86.lib")
#endif // _WIN64

#include <windows.h>

#endif

#include <Processing.NDI.Lib.h>

// all the fields needed to configure the sender
typedef struct config_t {
    // identifier for the NDI instance (ex. stream name)
    const char* ndiName;
    // audio
    // sample-rate
	int sample_rate = 48000;
	// channels
	int no_channels = 1;
    // video
    int xres = 1920;
    int yres = 1080;
    NDIlib_FourCC_video_type_e fourCC = (NDIlib_FourCC_video_type_e) NDI_LIB_FOURCC('I', '4', '2', '0');
    //NDIlib_FourCC_video_type_e fourCC = NDIlib_FourCC_video_type_UYVY;
  	// frame-rate ex. NTSC is 30000,1001 = 30000/1001 = 29.97fps
	int frame_rate_N, frame_rate_D;
    // picture aspect ratio ex. 16.0/9.0 = 1.778 is 16:9 video
	// 0 means square pixels
	float picture_aspect_ratio;
} config_t;

// for stopping the main()
static std::atomic<bool> exit_loop(false);

static void sigint_handler(int) {
    exit_loop = true;
}

// global static reference for the JVM
static JavaVM *jvm;

/**
 * Sender implementation, sending out data via NDI.
 */
class NDISender {
    public:
        // pointer for ctx lookup of the instance
        uintptr_t selfId;
        // audio and video frame holders
        //NDIlib_audio_frame_v2_t NDI_audio_frame; // floats
        NDIlib_audio_frame_interleaved_16s_t NDI_audio_frame; // shorts
        NDIlib_video_frame_v2_t NDI_video_frame;
        // receiver
        jobject receiver;
        jclass receiverClass;
        // ndi sender
        NDIlib_send_instance_t ndiSender;
        // shutdown flag
        bool shutdown = false;
        // debug flag
        bool debug = false;

        NDISender();

        virtual ~NDISender() {
            try {
            	// Destroy the NDI sender
	            NDIlib_send_destroy(ndiSender);
                // clean up jvm stuff
                if (receiver != nullptr) {
                    JNIEnv *env;
                    jvm->GetEnv((void **) &env, JNI_VERSION_1_8);
                    env->DeleteGlobalRef(receiver);
                    env->DeleteGlobalRef(receiverClass);
                }
            } catch(...) {};
            // Free the frame holders
            free((void*) NDI_video_frame.p_data);
            free((void*) NDI_audio_frame.p_data);
            std::cout << "freed sender: " << selfId << std::endl;
        };

        void sendData(std::vector<uint8_t> sendVector, int dataType);

        void sendAudio(std::vector<uint16_t> sendVector);

        void recvData(uint8_t *data, size_t data_len);

};

// generic context sender instance types
class NDIContext {
    public:
        NDIContext() {};
 
        NDISender* newSender() {
            NDISender *sender = new NDISender();
            if (sender != 0) {
                sender->selfId = (uintptr_t) sender;
                return sender;
            }
            return 0;
        }

        NDISender* getSender(long id) {
            uintptr_t locate = (uintptr_t) id;
            NDISender *sender = (NDISender *) locate;
            if (sender != 0) {
                return sender;
            }
            return 0;
        }

        long removeSender(long id) {
            //std::cout << "Calling remove on sender id: " << id << std::endl;
            uintptr_t locate = (uintptr_t) id;
            NDISender *sender = (NDISender *) locate;
            if (sender != 0) {
                delete sender;
            }
            return 0L;
        }

};

// without having this here, we'd get an undefined symbol
static NDIContext ndi_ctx;

// Main calls to create a sender/receiver for hitting their primary methods
class NDIMain {
    public:
        NDIMain() {};
        // create an sender instance
        long create_sender();
        // start process on an sender instance
        void * start(void *sender_arg, config_t *config);
        // stop the sender instance
        void * stop(void *sender_arg);
};

static NDIMain maininator;

#endif //CPPWRAPPER_NDI_H

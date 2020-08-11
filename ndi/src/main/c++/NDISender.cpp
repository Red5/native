
#pragma once

#include <chrono>

#include "NDIMain.h"

/* extern C used to prevent method mangling or other odd things from happening */
extern "C" {

    // ctor
    NDISender::NDISender() {
        selfId = 0;
        receiver = nullptr;
        receiverClass = nullptr;
        // configure the default fourCC we'll use
        NDI_video_frame.FourCC = (NDIlib_FourCC_video_type_e) NDI_LIB_FOURCC('I', '4', '2', '0');
    }

    // send typed data from bytes
    void NDISender::sendData(std::vector<uint8_t> sendVector, int dataType) {
        // set up a frame for the data and send based on type
        if (dataType == 1) { // video
            std::cout << "Send video " << NDI_video_frame.xres << "x" << NDI_video_frame.yres << " stride: " << NDI_video_frame.line_stride_in_bytes << " rate: " << NDI_video_frame.frame_rate_N << "/" << NDI_video_frame.frame_rate_D << " fourCC: " << NDI_video_frame.FourCC << " size: " << sendVector.size() << std::endl;
            NDI_video_frame.p_data = sendVector.data();
            // stride is simple in yuv420p as its the width of the frame
            NDI_video_frame.line_stride_in_bytes = NDI_video_frame.xres;
            //std::cout << "Stride: " << NDI_video_frame.line_stride_in_bytes << " size: " << NDI_video_frame.data_size_in_bytes << std::endl;
            NDIlib_send_send_video_v2(ndiSender, &NDI_video_frame);
        } else if (dataType == 2) { // audio
            std::cout << "Send audio " << NDI_audio_frame.sample_rate << "@" << NDI_audio_frame.no_channels << " size: " << sendVector.size() << std::endl;
            // XXX if we end-up sending audio via byte array here, we'd have to convert them to floats or shorts
        } else if (dataType == 3) { // metadata
            NDIlib_metadata_frame_t* p_metadata;
            //p_metadata->p_data = "<CAPTION service=\"1\" action=\"create\" standard=\"C708\" ><div id=\"0\" style=\"width:24%;height:6%;top:93%;left:30%;visibility:visible;z-index:7;text-align:left;\"><span>Hello World!</span></div></CAPTION>\0";
            p_metadata->p_data = (char *) sendVector.data();
            NDIlib_send_send_metadata(ndiSender, p_metadata);
        }
        // TODO fill in the reset of the types
    }

    // send audio from shorts
    void NDISender::sendAudio(std::vector<uint16_t> sendVector) {
        // audio
        std::cout << "Send audio " << NDI_audio_frame.sample_rate << "@" << NDI_audio_frame.no_channels << " size: " << sendVector.size() << std::endl;
        // should automagically cast?
        NDI_audio_frame.p_data = (int16_t *) sendVector.data();
        NDI_audio_frame.no_samples = sendVector.size();
        NDIlib_util_send_send_audio_interleaved_16s(ndiSender, &NDI_audio_frame);
    }

    // handle / dispatch the data received
    void NDISender::recvData(uint8_t *data, size_t data_len) {
        std::cout << "Received size " << data_len << std::endl;
        if (receiver != nullptr) {
            // good post about how this works http://adamish.com/blog/archives/327
            JNIEnv *env;
            int getEnvStat = jvm->GetEnv((void **) &env, JNI_VERSION_1_8);
            if (getEnvStat == JNI_EDETACHED) {
                //std::cout << "GetEnv: not attached" << std::endl;
                if (jvm->AttachCurrentThread((void **) &env, NULL) != 0) {
                    std::cerr << "Failed to attach" << std::endl;
                }
            } else if (getEnvStat == JNI_OK) {
                //std::cout << "GetEnv: attached" << std::endl;
            } else if (getEnvStat == JNI_EVERSION) {
                std::cerr << "GetEnv: version not supported" << std::endl;
            }
            //if (receiverMethodId == nullptr) {
                //jclass receiverClass = env->GetObjectClass(receiver);
                // public void receive(int clientId, int streamId, byte[] data)
                jmethodID receiverMethodId = env->GetMethodID(receiverClass, "receive", "([B)V");
            //}
            // create a new byte array to hold the buffer contents
            jbyteArray bytes = env->NewByteArray(data_len);
            env->SetByteArrayRegion(bytes, 0, data_len, (jbyte*) data);
            env->CallVoidMethod(receiver, receiverMethodId, bytes);
            // can't free "data" it since the SRTReceiver is using it
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
            }
            jvm->DetachCurrentThread();
        } else {
            std::cerr << "Java receiver is not available" << std::endl;
        }
    }

    /**
     * Create an instance and return a usable unique identifier.
     */
    JNIEXPORT jlong JNICALL Java_org_red5_ndi_NDISender_createSender(JNIEnv *env, jclass clazz) {
        return maininator.create_sender();
    }

    JNIEXPORT void JNICALL Java_org_red5_ndi_NDISender_start(JNIEnv *env, jclass clazz, jlong id, jobject config, jobject receiver) {
        std::cout << "Starting sender: " << id << std::endl;
        NDISender *sender = ndi_ctx.getSender(id);
        if (sender != 0) {
            // config for NDI from the incoming java config
            config_t *ndiConfig = (config_t*) malloc(sizeof(config_t));
            // get the configuration class
            jclass class_Config = env->GetObjectClass(config);
            /// name
            jstring strname = (jstring) env->CallObjectMethod(config, env->GetMethodID(class_Config, "getName", "()Ljava/lang/String;"));
            char *name = new char[128];
            jsize jlen = env->GetStringLength(strname);
            env->GetStringUTFRegion(strname, 0, jlen, name);
            ndiConfig->ndiName = name;
            /// width / height
            int width = (int) env->GetIntField(config, env->GetFieldID(class_Config, "width", "I"));
            int height = (int) env->GetIntField(config, env->GetFieldID(class_Config, "height", "I"));
            if (width > 0 && height > 0) {
                ndiConfig->xres = width;
                ndiConfig->yres = height;
                std::cout << "sender " << ndiConfig->xres << ":" << ndiConfig->yres << std::endl;
            }
            /// numerator / denominator
            int numerator = (int) env->GetIntField(config, env->GetFieldID(class_Config, "numerator", "I"));
            int denominator = (int) env->GetIntField(config, env->GetFieldID(class_Config, "denominator", "I"));
            if (numerator > 0 && denominator > 0) {
                ndiConfig->frame_rate_N = numerator;
                ndiConfig->frame_rate_D = denominator;
            }
            float aspectRatio = (float) env->GetFloatField(config, env->GetFieldID(class_Config, "aspectRatio", "F"));
            if (aspectRatio > 0.f) {
                ndiConfig->picture_aspect_ratio = aspectRatio;
            }
            /// sampleRate / channels
            int sampleRate = (int) env->GetIntField(config, env->GetFieldID(class_Config, "sampleRate", "I"));
            int channels = (int) env->GetIntField(config, env->GetFieldID(class_Config, "channels", "I"));
            if (sampleRate > 0 && channels > 0) {
                ndiConfig->sample_rate = sampleRate;
                ndiConfig->no_channels = channels;
            }
            // get jvm so we can attach later and call receive method
            env->GetJavaVM(&jvm);
            // set direct pointer to receiver
            sender->receiver = env->NewGlobalRef(receiver);
            jclass receiverClass = env->GetObjectClass(receiver);
            sender->receiverClass = reinterpret_cast<jclass>(env->NewGlobalRef(receiverClass));
            // enter the blocking accept logic
            maininator.start(sender, ndiConfig);
        } else {
            std::cerr << "Failed to locate server" << std::endl;
        }
    }

    /**
     * Send typed data out.
     * 
     * @param id sender id
     * @param data byte array holding data to send out
     * @param dataType type of data in the byte array from NDIlib_frame_type_e
     */
    JNIEXPORT jint JNICALL Java_org_red5_ndi_NDISender_sendData(JNIEnv *env, jclass clazz, jlong id, jbyteArray data, jint dataType) {
        std::cout << "Write type: " << dataType << std::endl;
        int wrote = 0;
        NDISender *sender = ndi_ctx.getSender(id);
        if (sender != 0) {
            // package the bytes
            jsize buf_len = env->GetArrayLength(data);
            jbyte* buf = (jbyte*) malloc(buf_len);
            env->GetByteArrayRegion(data, 0, buf_len, buf);
            // send the data
            std::vector<uint8_t> sendVector(&buf[0], &buf[buf_len]);
            sender->sendData(sendVector, dataType);
            // how many bytes did we send out...
            wrote = buf_len;
        }
        return wrote;
    }

    /**
     * Send audio data out.
     * 
     * @param id sender id
     * @param data short array holding data to send out
     */
    JNIEXPORT jint JNICALL Java_org_red5_ndi_NDISender_sendAudio(JNIEnv *env, jclass clazz, jlong id, jshortArray data) {
        std::cout << "Write" << std::endl;
        int wrote = 0;
        NDISender *sender = ndi_ctx.getSender(id);
        if (sender != 0) {
            // package the shorts
            jsize buf_len = env->GetArrayLength(data);
            jshort* buf = (jshort*) malloc(buf_len);
            env->GetShortArrayRegion(data, 0, buf_len, buf);
            // send the data
            std::vector<uint16_t> sendVector(&buf[0], &buf[buf_len]);
            sender->sendAudio(sendVector);
            // how many bytes did we send out...
            wrote = buf_len;
        }
        return wrote;
    }

    /**
     * Send video data out.
     * 
     * @param id sender id
     * @param data byte array holding data to send out
     */
    JNIEXPORT jint JNICALL Java_org_red5_ndi_NDISender_sendVideo(JNIEnv *env, jclass clazz, jlong id, jbyteArray data) {
        std::cout << "Write" << std::endl;
        int wrote = 0;
        NDISender *sender = ndi_ctx.getSender(id);
        if (sender != 0) {
            // package the bytes
            jsize buf_len = env->GetArrayLength(data);
            jbyte* buf = (jbyte*) malloc(buf_len);
            env->GetByteArrayRegion(data, 0, buf_len, buf);
            // send the data
            std::vector<uint8_t> sendVector(&buf[0], &buf[buf_len]);
            sender->sendData(sendVector, 1);
            // how many bytes did we send out...
            wrote = buf_len;
        }
        return wrote;
    }

    JNIEXPORT void JNICALL Java_org_red5_ndi_NDISender_stop(JNIEnv *env, jclass clazz, jlong id) {
        std::cout << "Stopping NDI sender: " << id << std::endl;
        NDISender *sender = ndi_ctx.getSender(id);
        if (sender != 0) {
            maininator.stop(sender);
        }
        ndi_ctx.removeSender(id);
    }

}

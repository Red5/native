#pragma once

#include "R5MpegMain.h"

/* extern C used to prevent method mangling or other odd things from happening */
extern "C" {

// callback for decoded mpeg1 video
void onVideo(plm_t *mpeg, plm_frame_t *frame, void *handler) {
    TSHandler *self = (TSHandler *) handler;
    // Hand the decoded data over to OpenGL. For the RGB texture mode, the
    // YCrCb->RGB conversion is done on the CPU.
    /*
    // decoded data passed back via receiver
    //self->recvData(decoded, decodedLength);

    if (self->texture_mode == APP_TEXTURE_MODE_YCRCB) {
        app_update_texture(self, GL_TEXTURE0, self->texture_y, &frame->y);
        app_update_texture(self, GL_TEXTURE1, self->texture_cb, &frame->cb);
        app_update_texture(self, GL_TEXTURE2, self->texture_cr, &frame->cr);
    }
    else {
        plm_frame_to_rgb(frame, self->rgb_data, frame->width * 3);
    
        glBindTexture(GL_TEXTURE_2D, self->texture_rgb);
        glTexImage2D(
            GL_TEXTURE_2D, 0, GL_RGB, frame->width, frame->height, 0,
            GL_RGB, GL_UNSIGNED_BYTE, self->rgb_data
        );
    }
    */
}

// callback for mp2 audio
void onAudio(plm_t *mpeg, plm_samples_t *samples, void *handler) {
    TSHandler *self = (TSHandler *) handler;
    /*
    // decoded data passed back via receiver
    //self->recvData(decoded, decodedLength);

    // Hand the decoded samples over to SDL
    int size = sizeof(float) * samples->count * 2;
    SDL_QueueAudio(self->audio_device, samples->interleaved, size);
    */
}

// ctor
TSHandler::TSHandler() {
}

bool TSHandler::init() {
    std::cout << "init: " << this << std::endl;
    // Create the map defining what datatype to map to what PID
    int pcrPid = 0;
    std::map<uint8_t, int> streamPidMap;
    if (config->videoPid > 0) {
        streamPidMap[TYPE_VIDEO] = config->videoPid;
        pcrPid = streamPidMap[TYPE_VIDEO];
    }
    if (config->audioPid > 0) {
        streamPidMap[TYPE_AUDIO] = config->audioPid;
        // if no audio-only use AUDIO_PID
        if (pcrPid == 0) {
            pcrPid = streamPidMap[TYPE_AUDIO];
        }
    }
    // Create demuxer / muxer and set onto the handler
    demuxer = std::make_shared<MpegTsDemuxer>();
    muxer = std::make_shared<MpegTsMuxer>(streamPidMap, PMT_PID, pcrPid);
    std::cout << "demuxer: " << demuxer << " muxer: " << muxer << std::endl;
    // set the demuxer callback
    demuxer->esOutCallback = std::bind(&TSHandler::onDemuxed, this, std::placeholders::_1);
    // set the muxer callback where TS packets are fed to
    muxer->tsOutCallback = std::bind(&TSHandler::onMuxed, this, std::placeholders::_1);
    //std::cout << "callbacks set" << std::endl;
    /*
    // figure out the right size for both audio and video
    size_t length = config->width * config->height * 3;
    uint8_t bytes[length];
    //
    plm_buffer_t *buffer = plm_buffer_create_with_memory(bytes, length, true);
    plm = plm_create_with_buffer(buffer, true);
    if (plm) {
        plm_set_video_decode_callback(plm, onVideo, this);
        plm_set_audio_decode_callback(plm, onAudio, this);
        return 0;
    }
    */
    return JNI_TRUE;
}

void TSHandler::decodeVideo(std::vector<uint8_t> vbuf) {
    std::cout << "Decode video " << config->width << "x" << config->height << " size: " << vbuf.size() << std::endl;
    // Compute the delta time since the last update
    //auto current_time = std::chrono::steady_clock::now();
    //std::chrono::duration<double> elapsed_time = current_time - last_time;
    //last_time = current_time;
    // plm->buffer = vbuf.data()
    // set plm->video_packet_type?
    plm_frame_t *frame = plm_decode_video(plm);
    //plm_decode(plm, elapsed_time.count() / 1000.0); // send as seconds
}

void TSHandler::decodeAudio(std::vector<uint16_t> abuf) {
    std::cout << "Decode audio " << config->sample_rate << "@" << config->no_channels << " size: " << abuf.size() << std::endl;
    // Compute the delta time since the last update
    //auto current_time = std::chrono::steady_clock::now();
    //std::chrono::duration<double> elapsed_time = current_time - last_time;
    //last_time = current_time;
    // should automagically cast?
    // plm->buffer = (int16_t *) abuf.data() // seems this wants an array of l&r interleaved floats
    // set plm->audio_packet_type?
    plm_samples_t *samples = plm_decode_audio(plm);
    //plm_decode(plm, elapsed_time.count() / 1000.0); // send as seconds
}

// hand / dispatch the data (bytes) back over to java via the receiver
void TSHandler::recvData(uint8_t *data, size_t data_len) {
    std::cout << "Received bytes size " << data_len << std::endl;
    if (receiver != nullptr) {
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
            // public void receive(byte[] data)
            jmethodID receiverMethodId = env->GetMethodID(receiverClass, "receive", "([B)V");
        //}
        // create a new byte array to hold the buffer contents
        jbyteArray bytes = env->NewByteArray(data_len);
        env->SetByteArrayRegion(bytes, 0, data_len, (jbyte*) data);
        env->CallVoidMethod(receiver, receiverMethodId, bytes);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        jvm->DetachCurrentThread();
    } else {
        std::cerr << "Java receiver is not available" << std::endl;
    }
}

// hand / dispatch the data (bytes) back over to java via the receiver
void TSHandler::recvData(uint8_t *data, size_t data_len, uint64_t pts, uint16_t pid) {
    std::cout << "Received bytes size " << data_len << " pts: " << pts << " pid: " << pid << std::endl;
    if (receiver != nullptr) {
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
            // public void receiveTyped(long timetamp, byte[] data, int typeId)
            jmethodID receiverMethodId = env->GetMethodID(receiverClass, "receiveTyped", "(J[BI)V");
        //}
        // determine the type id to hand back
        int typeId = 0; // TYPE_UNKNOWN
        if (pid == config->videoPid) {
            // assumes configured video pid is h264
            typeId = TYPE_H264;
        } else if (pid == config->audioPid) {
            // assumes configured audio pid is adts
            typeId = TYPE_ADTS;
        }
        // create a new byte array to hold the buffer contents
        jbyteArray bytes = env->NewByteArray(data_len);
        env->SetByteArrayRegion(bytes, 0, data_len, (jbyte*) data);
        std::cout << "Prepared for call pts: " << ((jlong) pts) << " typeId: " << ((jint) pid) << std::endl;
        env->CallVoidMethod(receiver, receiverMethodId, pts, bytes, typeId);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        jvm->DetachCurrentThread();
    } else {
        std::cerr << "Java receiver is not available" << std::endl;
    }
}

// hand / dispatch the data (shorts) back over to java via the receiver
void TSHandler::recvData(uint16_t *data, size_t data_len) {
    std::cout << "Received shorts size " << data_len << std::endl;
    // quick and dirty shorts to bytes
    //uint8_t *bdata = (uint8_t *) data;
    // short array is double the size of the bytes..
    //recvData(bdata, data_len * 2);
    if (receiver != nullptr) {
        JNIEnv *env;
        int getEnvStat = jvm->GetEnv((void **) &env, JNI_VERSION_1_8);
        if (getEnvStat == JNI_EDETACHED) {
            if (jvm->AttachCurrentThread((void **) &env, NULL) != 0) {
                std::cerr << "Failed to attach" << std::endl;
            }
        } else if (getEnvStat == JNI_OK) {
        } else if (getEnvStat == JNI_EVERSION) {
            std::cerr << "GetEnv: version not supported" << std::endl;
        }
        //if (receiverMethodId == nullptr) {
            //jclass receiverClass = env->GetObjectClass(receiver);
            // public void receive(short[] data)
            jmethodID receiverMethodId = env->GetMethodID(receiverClass, "receive", "([S)V");
        //}
        // create a new short array to hold the buffer contents
        jshortArray shorts = env->NewShortArray(data_len);
        env->SetShortArrayRegion(shorts, 0, data_len, (jshort*) data);
        env->CallVoidMethod(receiver, receiverMethodId, shorts);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        jvm->DetachCurrentThread();
    } else {
        std::cerr << "Java receiver is not available" << std::endl;
    }
}

// callback for the MPEG-TS demuxer
void TSHandler::onDemuxed(EsFrame *pEs) {
    std::cout << "Demuxed data " << unsigned(pEs->mStreamType) << " size: " << pEs->mData->size() << " broken? " << pEs->mBroken << std::endl;
    if (demuxer->mPmtIsValid) {
        // check the PMT header for our expected a/v types
        // demuxer.mPmtHeader
    }
    if (pEs->mBroken == 0) {
        // pass off to the recv to get it back over to java
        recvData(pEs->mData->data(), pEs->mData->size(), pEs->mPts, pEs->mPid);
    } else {
        // do we want to handle broken frames? these may not be broken, could be 0 pes.length h264 video
        std::cerr << "Broken frame detected" << std::endl;
        // pass off to the recv to get it back over to java
        recvData(pEs->mData->data(), pEs->mData->size(), pEs->mPts, pEs->mPid);
    }
}

// callback for the MPEG-TS muxer
void TSHandler::onMuxed(SimpleBuffer &rTsOutBuffer) {
    std::cout << "Muxed data size: " << rTsOutBuffer.size() << std::endl;
    // pass off to the recv to get it back over to java
    recvData(rTsOutBuffer.data(), rTsOutBuffer.size());
}

/**
 * Create an instance and return a usable unique identifier.
 */
JNIEXPORT jlong JNICALL Java_org_red5_mpeg_TSHandler_createHandler(JNIEnv *env, jclass clazz, jobject config, jobject receiver) {
    std::cout << "Create handler config: " << config << " receiver: " << receiver << std::endl;
    jlong id = maininator.create_handler();
    TSHandler *handler = mpeg_ctx.getHandler(id);
    if (handler != 0) {
        // config for the incoming java config
        config_t *mpegConfig = (config_t*) malloc(sizeof(config_t));
        // get the configuration class
        jclass class_Config = env->GetObjectClass(config);
        /// name
        jstring strname = (jstring) env->CallObjectMethod(config, env->GetMethodID(class_Config, "getName", "()Ljava/lang/String;"));
        char *name = new char[128];
        jsize jlen = env->GetStringLength(strname);
        env->GetStringUTFRegion(strname, 0, jlen, name);
        mpegConfig->mpegName = name;
        /// width / height
        int width = (int) env->GetIntField(config, env->GetFieldID(class_Config, "width", "I"));
        int height = (int) env->GetIntField(config, env->GetFieldID(class_Config, "height", "I"));
        if (width > 0 && height > 0) {
            mpegConfig->width = width;
            mpegConfig->height = height;
            std::cout << "handler " << mpegConfig->width << ":" << mpegConfig->height << std::endl;
        }
        /// sampleRate / channels
        int sampleRate = (int) env->GetIntField(config, env->GetFieldID(class_Config, "sampleRate", "I"));
        int channels = (int) env->GetIntField(config, env->GetFieldID(class_Config, "channels", "I"));
        if (sampleRate > 0 && channels > 0) {
            mpegConfig->sample_rate = sampleRate;
            mpegConfig->no_channels = channels;
        }
        // mpeg-ts options
        uint16_t pmtPid = (uint16_t) env->GetShortField(config, env->GetFieldID(class_Config, "pmtPid", "S"));
        if (pmtPid > 0) {
            mpegConfig->pmtPid = pmtPid;
        }
        uint16_t audioPid = (uint16_t) env->GetShortField(config, env->GetFieldID(class_Config, "audioPid", "S"));
        if (audioPid > 0) {
            mpegConfig->audioPid = audioPid;
        }
        uint16_t videoPid = (uint16_t) env->GetShortField(config, env->GetFieldID(class_Config, "videoPid", "S"));
        if (videoPid > 0) {
            mpegConfig->videoPid = videoPid;
        }
        uint16_t metaPid = (uint16_t) env->GetShortField(config, env->GetFieldID(class_Config, "metaPid", "S"));
        if (metaPid > 0) {
            mpegConfig->metaPid = metaPid;
        }
        uint8_t streamId = (uint8_t) env->GetByteField(config, env->GetFieldID(class_Config, "streamId", "B"));
        if (streamId > 0) {
            mpegConfig->streamId = streamId;
        }
        // set the config on the handler
        handler->config = mpegConfig;
        // get jvm so we can attach later and call receive method
        env->GetJavaVM(&jvm);
        // set direct pointer to receiver
        handler->receiver = env->NewGlobalRef(receiver);
        jclass receiverClass = env->GetObjectClass(receiver);
        handler->receiverClass = reinterpret_cast<jclass>(env->NewGlobalRef(receiverClass));
        // initialize the handler
        if (!maininator.init(handler)) {
            std::cerr << "Initialize failed" << std::endl;
        }
    } else {
        std::cerr << "Failed to locate handler" << std::endl;
    }
    return id;
}

/**
 * Send audio data out.
 * 
 * @param id handler id
 * @param data short array holding data to send out
 * @return true if decoder accepted the data and false otherwise
 */
JNIEXPORT jboolean JNICALL Java_org_red5_mpeg_TSHandler_decodeAudio(JNIEnv *env, jclass clazz, jlong id, jshortArray data) {
    std::cout << "Write" << std::endl;
    TSHandler *handler = mpeg_ctx.getHandler(id);
    if (handler != 0) {
        // package the shorts
        jsize buf_len = env->GetArrayLength(data);
        jshort* buf = (jshort*) malloc(buf_len);
        env->GetShortArrayRegion(data, 0, buf_len, buf);
        std::vector<uint16_t> abuf(&buf[0], &buf[buf_len]);
        handler->decodeAudio(abuf);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

/**
 * Decode video data.
 * 
 * @param id handler id
 * @param data byte array holding data to decode
 * @return true if decoder accepted the data and false otherwise
 */
JNIEXPORT jboolean JNICALL Java_org_red5_mpeg_TSHandler_decodeVideo(JNIEnv *env, jclass clazz, jlong id, jbyteArray data) {
    std::cout << "Decode" << std::endl;
    TSHandler *handler = mpeg_ctx.getHandler(id);
    if (handler != 0) {
        // package the bytes
        jsize buf_len = env->GetArrayLength(data);
        jbyte* buf = (jbyte*) malloc(buf_len);
        env->GetByteArrayRegion(data, 0, buf_len, buf);
        std::vector<uint8_t> vbuf(&buf[0], &buf[buf_len]);
        handler->decodeVideo(vbuf);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

/**
 * Demux MPEG-TS data. Resulting demuxed data will be returned via callback / receiver.
 * 
 * @param id handler id
 * @param data byte array holding data to demux
 */
JNIEXPORT void JNICALL Java_org_red5_mpeg_TSHandler_demux(JNIEnv *env, jclass clazz, jlong id, jbyteArray data) {
    std::cout << "Demux" << std::endl;
    TSHandler *handler = mpeg_ctx.getHandler(id);
    if (handler != 0) {
        jsize buf_len = env->GetArrayLength(data);
        jbyte* buf = (jbyte*) malloc(buf_len);
        env->GetByteArrayRegion(data, 0, buf_len, buf);
        //std::cout << "Sync byte? " << unsigned(buf[0]) << std::endl;
        SimpleBuffer in;
        in.append((uint8_t*) &buf[0], buf_len);
        handler->demuxer->decode(in);
    }
}

/**
 * Mux MPEG-TS data. Resulting muxed data will be returned via callback / receiver.
 * 
 * @param id handler id
 * @param data byte array holding data to mux
 * @param pts presentation timestamp
 * @param type stream type
 * @param pid
 * @param streamId ES stream id for the data
 */
JNIEXPORT void JNICALL Java_org_red5_mpeg_TSHandler_mux(JNIEnv *env, jclass clazz, jlong id, jbyteArray data, jlong pts, jbyte type, jshort pid, jbyte streamId) {
    std::cout << "Mux" << std::endl;
    TSHandler *handler = mpeg_ctx.getHandler(id);
    if (handler != 0) {
        jsize buf_len = env->GetArrayLength(data);
        jbyte* buf = (jbyte*) malloc(buf_len);
        env->GetByteArrayRegion(data, 0, buf_len, buf);
        // Build a frame of data (ES)
        EsFrame esFrame;
        esFrame.mData = std::make_shared<SimpleBuffer>();
        // Append your ES-Data
        esFrame.mData->append((uint8_t*) &buf[0], buf_len);
        esFrame.mPts = pts;
        esFrame.mDts = pts;
        esFrame.mPcr = 0;
        esFrame.mStreamType = type;
        esFrame.mStreamId = streamId;
        esFrame.mPid = pid;
        esFrame.mExpectedPesPacketLength = 0;
        esFrame.mCompleted = true;
        // Multiplex your data
        handler->muxer->encode(esFrame);
    }
}

JNIEXPORT void JNICALL Java_org_red5_mpeg_TSHandler_destroy(JNIEnv *env, jclass clazz, jlong id) {
    std::cout << "Destroying TS handler: " << id << std::endl;
    maininator.destroy(id);
}

}

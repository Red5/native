#pragma once

#include <cmath>

// NDI "main" wrapper
#include "NDIMain.h"

/* extern C used to prevent method mangling or other odd things from happening */
extern "C" {

    // This will act as our source and just provide us with a yuv420p frame when requested
    std::vector<uint8_t> getFrameData(int i) {
        std::string fileName;
        if (i < 10) {
            fileName = "/home/mondain/workspace/github-red5/native/ndi/media/image-00" + std::to_string(i) + ".raw";
        } else if (i < 100) {
            fileName = "/home/mondain/workspace/github-red5/native/ndi/media/image-0" + std::to_string(i) + ".raw";
        } else {
            fileName = "/home/mondain/workspace/github-red5/native/ndi/media/image-" + std::to_string(i) + ".raw";
        }
        std::cout << "File name: " << fileName << std::endl;
        FILE *f = fopen(fileName.c_str(), "rb");
        if (!f) {
            std::cout << "Failed opening file" << std::endl;
            std::vector<uint8_t> empty;
            return empty;
        }
        fseek(f, 0, SEEK_END);
        size_t fSize = ftell(f);
        fseek(f, 0, SEEK_SET);
        uint8_t *pictureBuffer = (uint8_t *)malloc(fSize);
        if (!pictureBuffer) {
            std::cout << "Failed reserving memory" << std::endl;
            std::vector<uint8_t> empty;
            return empty;
        }
        size_t fResult = fread(pictureBuffer, 1, fSize, f);
        if (fResult != fSize) {
            std::cout << "Failed reading data" << std::endl;
            std::vector<uint8_t> empty;
            return empty;
        }
        std::vector<uint8_t> my_vector(&pictureBuffer[0], &pictureBuffer[fSize]);
        free(pictureBuffer);
        fclose(f);
        return my_vector;
    }

    /**
     * Test method for generated audio.
     */
    void generateAndSendAudio(void *sender_arg) {
        NDISender *sender = (NDISender *) sender_arg;
        float freq = 440.f;
        int seconds = 30;
        unsigned sample_rate = sender->NDI_audio_frame.sample_rate;
        size_t buf_size = seconds * sample_rate;
        short *samples;
        samples = new short[buf_size];
        for (int i = 0; i < buf_size; ++i) {
            samples[i] = 32760 * sin((2.f * float(3.14f) * freq) / sample_rate * i);
        }
        // how big is one second of audio samples
        int audioChunkSize = buf_size / seconds;
        int audioIndex = 0;
        uint64_t pts = 0;
        // testing with 787 yuv files
        while (audioIndex < buf_size) {
            // break the samples into seconds
            if (pts % 1000 == 0 && audioIndex < buf_size) {
                std::vector<uint16_t> audioVector(&samples[audioIndex], &samples[audioIndex + audioChunkSize]);
                sender->sendAudio(audioVector);
                audioIndex += audioChunkSize;
            }
            pts += 48000 / 60;
            std::this_thread::sleep_for(std::chrono::milliseconds(16)); // sleep for 16ms ~60Hz
        }
    }

    /**
     * Test method for video using existing raw yuv files.
     */
    void readAndSendVideo(void *sender_arg) {
        NDISender *sender = (NDISender *) sender_arg;
        // send sample yuv from test media
        uint64_t pts = 0;
        // testing with 787 yuv files
        for (int i = 0; i < 787; ++i) {
            std::vector<uint8_t> videoVector = getFrameData(i + 1);
            std::cout << "SendFrame > " << videoVector.size() << " pts " << pts << std::endl;
            sender->sendData(videoVector, 1);
            pts += 90000 / 60; // fake a pts of 60Hz. FYI.. the codestream is 23.98 (I and P only)
            std::this_thread::sleep_for(std::chrono::milliseconds(16)); // sleep for 16ms ~60Hz
        }
    }

    /**
     * Create a sender instance.
     */
    long NDIMain::create_sender() {
        // Not required, but "correct" (see the SDK documentation.
        if (!NDIlib_initialize()) {
            // Cannot run NDI. Most likely because the CPU is not sufficient (see SDK documentation).
            // you can check this directly with a call to NDIlib_is_supported_CPU()
            std::cerr << "Cannot run NDI" << std::endl;
            return 0;
        }
        // create the reference for this instance, so we can look it up
        NDISender *sender = ndi_ctx.newSender();
        if (sender != 0) {
            // get the identifier for return
            std::cout << "NDI sender created: " << sender->selfId << std::endl;       
            return sender->selfId;
        } else {
            std::cerr << "Failed to create sender" << std::endl;
        }
        return -1;
    }

    /**
     * Setup and start the sender. This also starts the event loop and blocks!
     */
    void * NDIMain::start(void *sender_arg, config_t *config) {
        // cast the arg back to sender
        NDISender *sender = (NDISender *) sender_arg;
        std::cout << "Start: " << sender->selfId << std::endl;
        // Create an NDI source that is called {streamName} and is clocked to the video.
        NDIlib_send_create_t NDI_send_create_desc;
        NDI_send_create_desc.p_ndi_name = config->ndiName;
        // the actual sender
        sender->ndiSender = NDIlib_send_create(&NDI_send_create_desc);
        if (!sender->ndiSender) {
            std::cout << "NDI sender failed to start" << std::endl;
            stop(sender);
        } else if (sender->debug) {
            std::cout << "Start " << sender->NDI_video_frame.xres << "x" << sender->NDI_video_frame.yres << " fourCC: " << sender->NDI_video_frame.FourCC << std::endl;
            // We are going to create a 640x480 interlaced frame at 29.97Hz.
            sender->NDI_video_frame.xres = config->xres;
            sender->NDI_video_frame.yres = config->yres;
            // Create an audio buffer
            sender->NDI_audio_frame.sample_rate = config->sample_rate;
            sender->NDI_audio_frame.no_channels = config->no_channels;
            // generate some sine wave audio
            std::thread audioThread(generateAndSendAudio, sender);
            std::thread videoThread(readAndSendVideo, sender);
            // wait for video thread to finish
            videoThread.join();
            // while we're testing from main() call stop here after we send all the frames
            stop(sender);
        }
        //Run this sender until ........
        do {
            std::this_thread::sleep_for(std::chrono::seconds(7));
        } while (!sender->shutdown);
        std::cout << "Exiting start" << std::endl;
    }

    void * NDIMain::stop(void *sender_arg) {     
        std::cout << "stop_server" << std::endl;   
        // cast the arg back to sender
        NDISender *sender = (NDISender *) sender_arg;
        //std::cout << "Stop: " << sender->selfId << std::endl;
        sender->shutdown = true;
        // Not required, but nice
        NDIlib_destroy();
    }

    /**
     * To launch the sender test ./NDIMain
     * 
     * Source ideas from examples at: https://github.com/Unit-X
     */
    int main(int argc, char *argv[]) {
        //std::cout << "Main args: " << argc << " arguments:" << std::endl;
        //for (int i = 0; i < argc; ++i) {
        //    std::cout << argv[i] << std::endl;
        //}
        // Catch interrupt so that we can shut down gracefully
        signal(SIGINT, sigint_handler);
        // create a sender instance
        long id = maininator.create_sender();
        if (id != -1) {
            NDISender *sender = ndi_ctx.getSender(id);
            sender->debug = true;
            std::cout << "Calling start on id: " << id << std::endl;
            // config for NDI
            config_t *ndiConfig = (config_t*) malloc(sizeof(config_t));
            ndiConfig->ndiName = "NDI Test Source";
            ndiConfig->xres = 640;
            ndiConfig->yres = 480;
            ndiConfig->sample_rate = 48000;
            ndiConfig->no_channels = 1;
            // runs the test sender above
            maininator.start(sender, ndiConfig); // this blocks
            //maininator.stop(sender);
            // remove from the context
            ndi_ctx.removeSender(id);
        }
        std::cout << "Exiting" << std::endl;
        return 0;
    }

}
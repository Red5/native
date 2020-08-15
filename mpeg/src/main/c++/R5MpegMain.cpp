#pragma once

#include <cmath>

// NDI "main" wrapper
#include "R5MpegMain.h"

/* extern C used to prevent method mangling or other odd things from happening */
extern "C" {

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

    /**
     * Create a handler instance.
     */
    long R5MpegMain::create_handler() {
        // create the reference for this instance, so we can look it up
        TSHandler *handler = mpeg_ctx.newHandler();
        if (handler != 0) {
            // get the identifier for return
            std::cout << "NDI handler created: " << handler->selfId << std::endl;       
            return handler->selfId;
        } else {
            std::cerr << "Failed to create handler" << std::endl;
        }
        return -1;
    }

    /**
     * Initialize the handler.
     */
    void * R5MpegMain::init(void *handler_arg) {
        // cast the arg back to handler
        TSHandler *handler = (TSHandler *) handler_arg;
        std::cout << "Init: " << handler->selfId << std::endl;
        // figure out the right size for both audio and video
        uint8_t *bytes;
        size_t length;

        plm_buffer_t *buffer = plm_buffer_create_with_memory(bytes, length, true);
        handler->plm = plm_create_with_buffer(buffer, true);
	    if (handler->plm) {
            plm_set_video_decode_callback(handler->plm, onVideo, handler);
            plm_set_audio_decode_callback(handler->plm, onAudio, handler);
	    } else {
            // init failed
        }
    }

    void * R5MpegMain::destroy(long id) {     
        std::cout << "destroy" << std::endl;
        mpeg_ctx.removeHandler(id);
    }

}

/*

int main(int argc, char *argv[]) {
	if (argc < 2) {
		printf("Usage: pl_mpeg_extract_frames <file.mpg>\n");
		return 1;
	}
	plm_t *plm = plm_create_with_filename(argv[1]);
	if (!plm) {
		printf("Couldn't open file");
		return 1;
	}
	plm_set_audio_enabled(plm, FALSE);
	int w = plm_get_width(plm);
	int h = plm_get_height(plm);
	uint8_t *rgb_buffer = (uint8_t *)malloc(w * h * 3);
	char png_name[16];
	plm_frame_t *frame = NULL;
	for (int i = 0; frame = plm_decode_video(plm); i++) {
		plm_frame_to_rgb(frame, rgb_buffer, w * 3);
		sprintf(png_name, "%04d.png", i);
		printf("Writing %s\n", png_name);
		stbi_write_png(png_name, w, h, 3, rgb_buffer, w * 3);
	}
    return 0;
}
*/

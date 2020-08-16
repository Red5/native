#pragma once

// TS-MPEG/MPEG1/MP2 "main" wrapper
#include "R5MpegMain.h"

/* extern C used to prevent method mangling or other odd things from happening */
extern "C" {

    /**
     * Create a handler instance.
     */
    long R5MpegMain::create_handler() {
        // create the reference for this instance, so we can look it up
        TSHandler *handler = mpeg_ctx.newHandler();
        if (handler != 0) {
            // get the identifier for return
            std::cout << "TS handler created: " << handler->selfId << std::endl;       
            return handler->selfId;
        } else {
            std::cerr << "Failed to create handler" << std::endl;
        }
        return -1;
    }

    /**
     * Initialize the handler.
     */
    bool R5MpegMain::init(void *handler_arg) {
        // cast the arg back to handler
        TSHandler *handler = (TSHandler *) handler_arg;
        std::cout << "Init: " << handler->selfId << std::endl;
        return handler->init();
    }

    void R5MpegMain::destroy(long id) {     
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

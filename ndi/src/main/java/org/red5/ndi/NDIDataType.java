package org.red5.ndi;

/**
 * Type descriptor for NDI data.
 * 
 * <ul>
 * <li>NDIlib_frame_type_none = 0</li>
 * <li>NDIlib_frame_type_video = 1</li>
 * <li>NDIlib_frame_type_audio = 2</li>
 * <li>NDIlib_frame_type_metadata = 3</li>
 * <li>NDIlib_frame_type_error = 4</li>
 * </ul>
 */
public enum NDIDataType {

    none, video, audio, metadata, error;

}
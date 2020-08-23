package org.red5.mpeg;

import java.util.*;

/**
 * Payload types we'll expect at time of writing. Any h.264 or h.265 will be expected in Annex-B format.
 */
public enum PayloadType {

    TYPE_UNKNOWN(0), TYPE_AUDIO(8), TYPE_VIDEO(9), TYPE_META(12), TYPE_I420('I', '4', '2', '0'), TYPE_ADTS('A', 'D', 'T', 'S'),
    TYPE_H264('H', '2', '6', '4'), TYPE_HEVC('H', 'E', 'V', 'C'),
    TYPE_MP2A(('M'<<24) | ('P'<<16) | ('2'<<8) | 'A'), TYPE_MP1V(('M'<<24) | ('P'<<16) | ('1'<<8) | 'V'),
    TYPE_ID3(('I'<<24) | ('D'<<16) | ('3'<<8) | ' '), TYPE_KLV(('K'<<24) | ('L'<<16) | ('V'<<8) | 'A');

    public final Integer typeId;

    static final Map<Integer, PayloadType> BY_VALUE = new HashMap<>();

    static {
        for (PayloadType e: values()) {
            BY_VALUE.put(e.typeId, e);
        }
    }

    PayloadType(char... typeArray) {
        // big-endian to match C/C++ side
        this.typeId = ((typeArray[0] << 24) | (typeArray[1] << 16) | (typeArray[2] << 8) | typeArray[3]);
    }

    PayloadType(int typeId) {
        this.typeId = typeId;
    }

    public int getTypeId() {
        return typeId;
    }

    public static PayloadType valueOfTypeId(int typeId) {
        return BY_VALUE.get(typeId);
    }
}
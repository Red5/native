#pragma once

// Prefixes used
// m class member
// p pointer (*)
// r reference (&)
// l local scope

#include "ts_packet.h"
#include "simple_buffer.h"

#include <cstdint>
#include <memory>
#include <map>
#include <functional>
#include <mutex>

class MpegTsDemuxer {
public:
    MpegTsDemuxer();

    virtual ~MpegTsDemuxer();

    uint8_t decode(SimpleBuffer &rIn);

    std::function<void(EsFrame *pEs)> esOutCallback = nullptr;
    std::function<void(uint64_t lPcr)> pcrOutCallback = nullptr;

    // stream, pid
    std::map<uint8_t, int> mStreamPidMap;
    int mPmtId;

    // PAT
    PATHeader mPatHeader;
    bool mPatIsValid = false;

    // PMT
    PMTHeader mPmtHeader;
    bool mPmtIsValid = false;
	
	///Delete copy and move constructors and assign operators
	MpegTsDemuxer(MpegTsDemuxer const &) = delete;              // Copy construct
	MpegTsDemuxer(MpegTsDemuxer &&) = delete;                   // Move construct
	MpegTsDemuxer &operator=(MpegTsDemuxer const &) = delete;   // Copy assign
	MpegTsDemuxer &operator=(MpegTsDemuxer &&) = delete;        // Move assign

private:
    // pid, Elementary data frame
    std::map<int, std::shared_ptr<EsFrame>> mEsFrames;
    int mPcrId;
    SimpleBuffer mRestData;
};


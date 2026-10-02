#ifndef AUDIO_ENGINE_H
#define AUDIO_ENGINE_H

#include <cstdint>
#include <cstddef>
#include <atomic>
#include <aaudio/AAudio.h>

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool start(int sampleRate = 44100);
    void stop();
    void pause();
    void resume();
    void reset();

    void writeSamples(const int16_t* samples, size_t numFrames);
    void setMuted(bool muted);
    void setVolume(float volume);
    int getSampleRate() const { return mSampleRate; }

    aaudio_data_callback_result_t onAudioReady(
        void* audioData,
        int32_t numFrames);

private:
    static constexpr size_t RING_BUFFER_CAPACITY_FRAMES = 8192;
    int16_t mRingBuffer[RING_BUFFER_CAPACITY_FRAMES * 2];
    std::atomic<size_t> mWriteHead{0};
    std::atomic<size_t> mReadHead{0};

    AAudioStream* mStream = nullptr;
    int mSampleRate = 44100;
    std::atomic<bool> mMuted{false};
    std::atomic<float> mVolume{1.0f};
    std::atomic<bool> mIsRunning{false};
};

#endif // AUDIO_ENGINE_H

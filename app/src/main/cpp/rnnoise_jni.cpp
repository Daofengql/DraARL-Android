#include <jni.h>

#include <algorithm>
#include <vector>

extern "C" {
#include "rnnoise.h"
}

namespace {

constexpr int kRnnoiseFrameSamples = 480;
constexpr int kInputSampleRate = 16'000;
constexpr int kRnnoiseSampleRate = 48'000;
constexpr int kResampleRatio = kRnnoiseSampleRate / kInputSampleRate;

struct NativeDenoiser {
    RNNModel* model = nullptr;
    DenoiseState* state = nullptr;
};

float clampSample(float value) {
    return std::max(-32768.0f, std::min(32767.0f, value));
}

NativeDenoiser* fromHandle(jlong handle) {
    return reinterpret_cast<NativeDenoiser*>(handle);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_cn_silverdragon_draarl_radio_RnnoiseNative_nativeCreate(
    JNIEnv*,
    jclass
) {
    auto* denoiser = new NativeDenoiser();
    denoiser->state = rnnoise_create(nullptr);
    if (denoiser->state == nullptr) {
        delete denoiser;
        return 0;
    }
    return reinterpret_cast<jlong>(denoiser);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_silverdragon_draarl_radio_RnnoiseNative_nativeDestroy(
    JNIEnv*,
    jclass,
    jlong handle
) {
    auto* denoiser = fromHandle(handle);
    if (denoiser == nullptr) return;
    rnnoise_destroy(denoiser->state);
    if (denoiser->model != nullptr) rnnoise_model_free(denoiser->model);
    delete denoiser;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_silverdragon_draarl_radio_RnnoiseNative_nativeReset(
    JNIEnv*,
    jclass,
    jlong handle
) {
    auto* denoiser = fromHandle(handle);
    if (denoiser == nullptr) return JNI_FALSE;
    rnnoise_destroy(denoiser->state);
    denoiser->state = rnnoise_create(denoiser->model);
    return denoiser->state == nullptr ? JNI_FALSE : JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_silverdragon_draarl_radio_RnnoiseNative_nativeProcess(
    JNIEnv* env,
    jclass,
    jlong handle,
    jshortArray samples
) {
    auto* denoiser = fromHandle(handle);
    if (denoiser == nullptr || denoiser->state == nullptr || samples == nullptr) {
        return JNI_FALSE;
    }

    const jsize inputLength = env->GetArrayLength(samples);
    if (inputLength <= 0) return JNI_TRUE;

    jboolean isCopy = JNI_FALSE;
    jshort* pcm = env->GetShortArrayElements(samples, &isCopy);
    if (pcm == nullptr) return JNI_FALSE;

    std::vector<float> upsampled(static_cast<size_t>(inputLength) * kResampleRatio);
    for (jsize index = 0; index < inputLength; ++index) {
        const float current = static_cast<float>(pcm[index]);
        const float next = index + 1 < inputLength
            ? static_cast<float>(pcm[index + 1])
            : current;
        const float delta = (next - current) / static_cast<float>(kResampleRatio);
        for (int step = 0; step < kResampleRatio; ++step) {
            upsampled[static_cast<size_t>(index) * kResampleRatio + step] =
                current + delta * static_cast<float>(step);
        }
    }

    std::vector<float> frame(kRnnoiseFrameSamples);
    std::vector<float> denoised(frame.size());
    const size_t outputLength = upsampled.size();
    for (size_t offset = 0; offset < outputLength; offset += kRnnoiseFrameSamples) {
        const size_t available = std::min(
            static_cast<size_t>(kRnnoiseFrameSamples),
            outputLength - offset
        );
        std::fill(frame.begin(), frame.end(), available > 0 ? upsampled[offset + available - 1] : 0.0f);
        std::copy_n(upsampled.begin() + static_cast<std::ptrdiff_t>(offset), available, frame.begin());
        rnnoise_process_frame(denoiser->state, denoised.data(), frame.data());

        const size_t inputStart = offset / kResampleRatio;
        const size_t inputEnd = std::min(
            static_cast<size_t>(inputLength),
            (offset + available + kResampleRatio - 1) / kResampleRatio
        );
        for (size_t inputIndex = inputStart; inputIndex < inputEnd; ++inputIndex) {
            const size_t relative = (inputIndex * kResampleRatio) - offset;
            float sample = 0.0f;
            for (int step = 0; step < kResampleRatio; ++step) {
                const size_t frameIndex = std::min(
                    static_cast<size_t>(kRnnoiseFrameSamples - 1),
                    relative + static_cast<size_t>(step)
                );
                sample += denoised[frameIndex];
            }
            pcm[inputIndex] = static_cast<jshort>(
                clampSample(sample / static_cast<float>(kResampleRatio))
            );
        }
    }

    env->ReleaseShortArrayElements(samples, pcm, 0);
    return JNI_TRUE;
}

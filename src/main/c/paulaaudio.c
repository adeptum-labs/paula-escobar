/*
 * Paula Escobar is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adam Waldenberg, Adeptum AB, Org.nr 559494-1824.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Website: https://www.adeptum.se
 * Contact: info@adeptum.se
 */
#define MINIAUDIO_IMPLEMENTATION
#define MA_NO_DECODING
#define MA_NO_ENCODING
#define MA_NO_GENERATION
#define MA_NO_RESOURCE_MANAGER
#define MA_NO_NODE_GRAPH
#define MA_NO_ENGINE
#include "miniaudio.h"

#include <stdint.h>
#include <string.h>

enum { CHANNELS = 2, RING_PERIODS = 4, FRAME_BYTES = CHANNELS * sizeof(int16_t) };

/* Numbered as the Java side numbers them; 0 is auto and tries the real backends in this order. */
static const ma_backend BACKENDS[] = {
    ma_backend_null,
    ma_backend_pulseaudio,
    ma_backend_alsa,
    ma_backend_jack,
    ma_backend_coreaudio,
    ma_backend_wasapi,
    ma_backend_null
};
static const ma_backend AUTO_ORDER[] = {
    ma_backend_wasapi, ma_backend_coreaudio, ma_backend_pulseaudio, ma_backend_alsa, ma_backend_jack
};

static ma_context context;
static ma_device device;
static ma_pcm_rb ring;
static ma_event space;
static volatile ma_bool32 stopped;
static ma_result last_failure = MA_SUCCESS;

static int fail(ma_result result)
{
    last_failure = result;
    return (int) result;
}

static void pull(ma_device *unused, void *output, const void *input, ma_uint32 frame_count)
{
    int16_t *out = output;
    ma_uint32 remaining = frame_count;
    (void) unused;
    (void) input;
    while (remaining > 0) {
        ma_uint32 chunk = remaining;
        void *source;
        if (ma_pcm_rb_acquire_read(&ring, &chunk, &source) != MA_SUCCESS || chunk == 0) {
            break;
        }
        memcpy(out, source, chunk * FRAME_BYTES);
        ma_pcm_rb_commit_read(&ring, chunk);
        out += chunk * CHANNELS;
        remaining -= chunk;
    }
    memset(out, 0, remaining * FRAME_BYTES);
    ma_event_signal(&space);
}

static void notify(const ma_device_notification *notification)
{
    if (notification->type == ma_device_notification_type_stopped) {
        stopped = MA_TRUE;
        ma_event_signal(&space);
    }
}

int paula_audio_open(int backend, int sample_rate, int buffer_frames)
{
    const ma_backend *order = backend == 0 ? AUTO_ORDER : &BACKENDS[backend];
    const ma_uint32 count = backend == 0 ? sizeof AUTO_ORDER / sizeof *AUTO_ORDER : 1;
    ma_context_config context_config = ma_context_config_init();
    ma_device_config config = ma_device_config_init(ma_device_type_playback);
    ma_result result;

    stopped = MA_FALSE;
    result = ma_context_init(order, count, &context_config, &context);
    if (result != MA_SUCCESS) {
        return fail(result);
    }
    result = ma_pcm_rb_init(ma_format_s16, CHANNELS, (ma_uint32) buffer_frames * RING_PERIODS, NULL, NULL, &ring);
    if (result != MA_SUCCESS) {
        ma_context_uninit(&context);
        return fail(result);
    }
    result = ma_event_init(&space);
    if (result != MA_SUCCESS) {
        ma_pcm_rb_uninit(&ring);
        ma_context_uninit(&context);
        return fail(result);
    }
    config.playback.format = ma_format_s16;
    config.playback.channels = CHANNELS;
    config.sampleRate = (ma_uint32) sample_rate;
    config.periodSizeInFrames = (ma_uint32) buffer_frames;
    config.dataCallback = pull;
    config.notificationCallback = notify;
    result = ma_device_init(&context, &config, &device);
    if (result == MA_SUCCESS) {
        result = ma_device_start(&device);
    }
    if (result != MA_SUCCESS) {
        ma_device_uninit(&device);
        ma_event_uninit(&space);
        ma_pcm_rb_uninit(&ring);
        ma_context_uninit(&context);
        return fail(result);
    }
    return 0;
}

int paula_audio_write(const int16_t *frames, int count)
{
    ma_uint32 remaining = (ma_uint32) count;
    while (remaining > 0) {
        ma_uint32 chunk = remaining;
        void *destination;
        if (stopped) {
            return fail(MA_DEVICE_NOT_STARTED);
        }
        if (ma_pcm_rb_acquire_write(&ring, &chunk, &destination) != MA_SUCCESS) {
            return fail(MA_ERROR);
        }
        if (chunk == 0) {
            ma_event_wait(&space);
            continue;
        }
        memcpy(destination, frames, chunk * FRAME_BYTES);
        ma_pcm_rb_commit_write(&ring, chunk);
        frames += chunk * CHANNELS;
        remaining -= chunk;
    }
    return 0;
}

void paula_audio_close(void)
{
    while (!stopped && ma_pcm_rb_available_read(&ring) > 0) {
        ma_event_wait(&space);
    }
    ma_device_uninit(&device);
    ma_event_uninit(&space);
    ma_pcm_rb_uninit(&ring);
    ma_context_uninit(&context);
}

const char *paula_audio_error(void)
{
    return ma_result_description(last_failure);
}

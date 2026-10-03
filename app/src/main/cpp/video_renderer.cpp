#include "video_renderer.h"
#include <algorithm>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "GbaVideoRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char* VERTEX_SHADER_SRC = R"(
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;

    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
        vTexCoord = aTexCoord;
    }
)";

static const char* FRAGMENT_SHADER_SRC = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uTexture;
    uniform int uFilterType;
    uniform vec2 uResolution;

    void main() {
        vec4 color = texture2D(uTexture, vTexCoord);

        if (uFilterType == 2) {
            // LCD grid emulation
            vec2 grid = fract(vTexCoord * vec2(240.0, 160.0));
            float factor = 1.0;
            if (grid.x < 0.1 || grid.y < 0.1) {
                factor = 0.85;
            }
            gl_FragColor = vec4(color.rgb * factor, 1.0);
        } else if (uFilterType == 3) {
            // CRT scanline emulation
            float scanline = sin(vTexCoord.y * 160.0 * 3.14159265);
            float factor = 1.0 - 0.20 * (scanline * scanline);
            gl_FragColor = vec4(color.rgb * factor, 1.0);
        } else {
            gl_FragColor = color;
        }
    }
)";

static GLuint loadShader(GLenum type, const char* shaderSrc) {
    GLuint shader = glCreateShader(type);
    if (shader == 0) return 0;

    glShaderSource(shader, 1, &shaderSrc, nullptr);
    glCompileShader(shader);

    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 1) {
            char* infoLog = new char[infoLen];
            glGetShaderInfoLog(shader, infoLen, nullptr, infoLog);
            LOGE("Error compiling shader:\n%s", infoLog);
            delete[] infoLog;
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

VideoRenderer::VideoRenderer() {}

VideoRenderer::~VideoRenderer() {
    if (mProgram != 0) {
        glDeleteProgram(mProgram);
        mProgram = 0;
    }
    if (mTextureId != 0) {
        glDeleteTextures(1, &mTextureId);
        mTextureId = 0;
    }
}

void VideoRenderer::init() {
    initShaders();

    // Generate and configure texture
    glGenTextures(1, &mTextureId);
    glBindTexture(GL_TEXTURE_2D, mTextureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, (mFilter == FILTER_BILINEAR) ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, (mFilter == FILTER_BILINEAR) ? GL_LINEAR : GL_NEAREST);

    // Initialize 240x160 blank texture with black
    uint32_t blank[GBA_WIDTH * GBA_HEIGHT] = {0};
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, GBA_WIDTH, GBA_HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, blank);

    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    LOGI("OpenGL ES VideoRenderer initialized successfully");
}

void VideoRenderer::initShaders() {
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, VERTEX_SHADER_SRC);
    GLuint fragmentShader = loadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SRC);

    mProgram = glCreateProgram();
    glAttachShader(mProgram, vertexShader);
    glAttachShader(mProgram, fragmentShader);
    glLinkProgram(mProgram);

    GLint linked = 0;
    glGetProgramiv(mProgram, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(mProgram, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 1) {
            char* infoLog = new char[infoLen];
            glGetProgramInfoLog(mProgram, infoLen, nullptr, infoLog);
            LOGE("Error linking program:\n%s", infoLog);
            delete[] infoLog;
        }
        glDeleteProgram(mProgram);
        mProgram = 0;
    }

    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);

    mPosAttrib = glGetAttribLocation(mProgram, "aPosition");
    mTexCoordAttrib = glGetAttribLocation(mProgram, "aTexCoord");
    mTextureUniform = glGetUniformLocation(mProgram, "uTexture");
    mFilterTypeUniform = glGetUniformLocation(mProgram, "uFilterType");
    mResolutionUniform = glGetUniformLocation(mProgram, "uResolution");
}

void VideoRenderer::setViewport(int width, int height) {
    mScreenWidth = width;
    mScreenHeight = height;
    calculateViewport();
}

void VideoRenderer::calculateViewport() {
    if (mScreenWidth <= 0 || mScreenHeight <= 0) return;

    if (mAspectMode == ASPECT_RATIO_STRETCH_FULL) {
        mViewportX = 0;
        mViewportY = 0;
        mViewportWidth = mScreenWidth;
        mViewportHeight = mScreenHeight;
    } else if (mAspectMode == ASPECT_RATIO_STRETCH) {
        // Safe Fullscreen: Narrows the two sides to 16:9 ratio in landscape mode
        // This expands the screen immersively without touching the camera notch / punch hole
        // and keeps the aspect ratio natural without severe horizontal stretching!
        float targetAspect = 16.0f / 9.0f; // 1.7778f
        float screenAspect = static_cast<float>(mScreenWidth) / static_cast<float>(mScreenHeight);

        if (screenAspect > targetAspect) {
            // Ultra-wide phone screen: Inset 2 sides to clean 16:9
            mViewportHeight = mScreenHeight;
            mViewportWidth = static_cast<int>(mScreenHeight * targetAspect);
            mViewportX = (mScreenWidth - mViewportWidth) / 2;
            mViewportY = 0;
        } else if (screenAspect > 1.5f) {
            // Screen between 3:2 and 16:9: Inset 5% on each side for safety
            int sideMargin = static_cast<int>(mScreenWidth * 0.05f);
            mViewportX = sideMargin;
            mViewportY = 0;
            mViewportWidth = mScreenWidth - 2 * sideMargin;
            mViewportHeight = mScreenHeight;
        } else {
            // Portrait mode: Fit width with true 3:2 GBA ratio
            float gbaAspect = 3.0f / 2.0f;
            mViewportWidth = mScreenWidth;
            mViewportHeight = static_cast<int>(mScreenWidth / gbaAspect);
            mViewportX = 0;
            mViewportY = (mScreenHeight - mViewportHeight) / 2;
        }
    } else if (mAspectMode == ASPECT_RATIO_1X) {
        mViewportWidth = GBA_WIDTH;
        mViewportHeight = GBA_HEIGHT;
        mViewportX = (mScreenWidth - mViewportWidth) / 2;
        mViewportY = (mScreenHeight - mViewportHeight) / 2;
    } else if (mAspectMode == ASPECT_RATIO_2X) {
        mViewportWidth = GBA_WIDTH * 2;
        mViewportHeight = GBA_HEIGHT * 2;
        mViewportX = (mScreenWidth - mViewportWidth) / 2;
        mViewportY = (mScreenHeight - mViewportHeight) / 2;
    } else if (mAspectMode == ASPECT_RATIO_3X) {
        mViewportWidth = GBA_WIDTH * 3;
        mViewportHeight = GBA_HEIGHT * 3;
        mViewportX = (mScreenWidth - mViewportWidth) / 2;
        mViewportY = (mScreenHeight - mViewportHeight) / 2;
    } else {
        // ASPECT_RATIO_FIT: 3:2
        float targetAspect = 3.0f / 2.0f;
        float screenAspect = static_cast<float>(mScreenWidth) / static_cast<float>(mScreenHeight);

        if (screenAspect > targetAspect) {
            // Pillarbox (bars on left/right)
            mViewportHeight = mScreenHeight;
            mViewportWidth = static_cast<int>(mScreenHeight * targetAspect);
            mViewportX = (mScreenWidth - mViewportWidth) / 2;
            mViewportY = 0;
        } else {
            // Letterbox (bars on top/bottom)
            mViewportWidth = mScreenWidth;
            mViewportHeight = static_cast<int>(mScreenWidth / targetAspect);
            mViewportX = 0;
            mViewportY = (mScreenHeight - mViewportHeight) / 2;
        }
    }
}

void VideoRenderer::updateTexture(const uint32_t* pixels) {
    if (!pixels || mTextureId == 0) return;

    glBindTexture(GL_TEXTURE_2D, mTextureId);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, GBA_WIDTH, GBA_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
}

void VideoRenderer::render() {
    if (mProgram == 0 || mTextureId == 0) return;

    glClear(GL_COLOR_BUFFER_BIT);
    glViewport(mViewportX, mViewportY, mViewportWidth, mViewportHeight);

    glUseProgram(mProgram);

    // Quad vertices and UVs
    static const GLfloat vertices[] = {
        -1.0f,  1.0f,
        -1.0f, -1.0f,
         1.0f,  1.0f,
         1.0f, -1.0f
    };

    static const GLfloat texCoords[] = {
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    };

    glEnableVertexAttribArray(mPosAttrib);
    glVertexAttribPointer(mPosAttrib, 2, GL_FLOAT, GL_FALSE, 0, vertices);

    glEnableVertexAttribArray(mTexCoordAttrib);
    glVertexAttribPointer(mTexCoordAttrib, 2, GL_FLOAT, GL_FALSE, 0, texCoords);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mTextureId);
    glUniform1i(mTextureUniform, 0);

    glUniform1i(mFilterTypeUniform, static_cast<int>(mFilter));
    glUniform2f(mResolutionUniform, static_cast<float>(mViewportWidth), static_cast<float>(mViewportHeight));

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(mPosAttrib);
    glDisableVertexAttribArray(mTexCoordAttrib);
}

void VideoRenderer::setFilter(VideoFilter filter) {
    mFilter = filter;
    if (mTextureId != 0) {
        glBindTexture(GL_TEXTURE_2D, mTextureId);
        GLint minMag = (mFilter == FILTER_BILINEAR) ? GL_LINEAR : GL_NEAREST;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minMag);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, minMag);
    }
}

void VideoRenderer::setAspectRatio(AspectRatioMode mode) {
    mAspectMode = mode;
    calculateViewport();
}

#ifndef VIDEO_RENDERER_H
#define VIDEO_RENDERER_H

#include <cstdint>
#include <GLES2/gl2.h>

enum VideoFilter {
    FILTER_NEAREST = 0,
    FILTER_BILINEAR = 1,
    FILTER_LCD = 2,
    FILTER_CRT = 3
};

enum AspectRatioMode {
    ASPECT_RATIO_FIT = 0,     // 3:2 maintain aspect ratio with letterboxing
    ASPECT_RATIO_STRETCH = 1, // Stretch to full screen
    ASPECT_RATIO_1X = 2,      // 240x160
    ASPECT_RATIO_2X = 3,      // 480x320
    ASPECT_RATIO_3X = 4       // 720x480
};

class VideoRenderer {
public:
    VideoRenderer();
    ~VideoRenderer();

    void init();
    void setViewport(int width, int height);
    void updateTexture(const uint32_t* pixels);
    void render();

    void setFilter(VideoFilter filter);
    void setAspectRatio(AspectRatioMode mode);

    static constexpr int GBA_WIDTH = 240;
    static constexpr int GBA_HEIGHT = 160;

private:
    void initShaders();
    void calculateViewport();

    GLuint mProgram = 0;
    GLuint mTextureId = 0;
    GLint mPosAttrib = -1;
    GLint mTexCoordAttrib = -1;
    GLint mTextureUniform = -1;
    GLint mFilterTypeUniform = -1;
    GLint mResolutionUniform = -1;

    int mScreenWidth = 0;
    int mScreenHeight = 0;
    int mViewportX = 0;
    int mViewportY = 0;
    int mViewportWidth = 0;
    int mViewportHeight = 0;

    VideoFilter mFilter = FILTER_NEAREST;
    AspectRatioMode mAspectMode = ASPECT_RATIO_FIT;
};

#endif // VIDEO_RENDERER_H

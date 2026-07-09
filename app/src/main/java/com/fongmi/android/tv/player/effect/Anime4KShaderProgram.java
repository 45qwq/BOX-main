package com.fongmi.android.tv.player.effect;

import android.content.Context;
import android.opengl.GLES20;

import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.effect.BaseGlShaderProgram;

/**
 * Anime4K 超分 shader 程序
 * 基于 anime4k v4 算法简化版：双边滤波降噪 + 边缘检测 + 锐化增强
 * 适合动漫/低分辨率视频的实时超分处理
 * strength: 0=低 1=中 2=高
 */
public class Anime4KShaderProgram extends BaseGlShaderProgram {

    private static final String VERTEX_SHADER =
            "attribute vec4 aFramePosition;\n" +
            "uniform mat4 uTransformationMatrix;\n" +
            "uniform mat4 uTexTransformationMatrix;\n" +
            "varying vec2 vTexSamplingCoord;\n" +
            "void main() {\n" +
            "  gl_Position = uTransformationMatrix * aFramePosition;\n" +
            "  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5,\n" +
            "                              aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);\n" +
            "  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;\n" +
            "}\n";

    // 根据强度生成 fragment shader
    // strength 0=低(0.3) 1=中(0.6) 2=高(1.0)
    private static String buildFragmentShader(int strength) {
        float strengthValue, sensitivityValue, sharpenFactor;
        if (strength == 0) {
            strengthValue = 0.3f;
            sensitivityValue = 0.08f;
            sharpenFactor = 0.2f;
        } else if (strength == 2) {
            strengthValue = 1.0f;
            sensitivityValue = 0.03f;
            sharpenFactor = 0.4f;
        } else {
            strengthValue = 0.6f;
            sensitivityValue = 0.05f;
            sharpenFactor = 0.3f;
        }
        return
            "precision highp float;\n" +
            "uniform sampler2D uTexSampler;\n" +
            "uniform vec2 uTexelSize;\n" +
            "varying vec2 vTexSamplingCoord;\n" +
            "#define STRENGTH " + strengthValue + "\n" +
            "#define SENSITIVITY " + sensitivityValue + "\n" +
            "#define SHARPEN " + sharpenFactor + "\n" +
            "float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }\n" +
            "void main() {\n" +
            "  vec3 center = texture2D(uTexSampler, vTexSamplingCoord).rgb;\n" +
            "  vec3 tl = texture2D(uTexSampler, vTexSamplingCoord + vec2(-uTexelSize.x, -uTexelSize.y)).rgb;\n" +
            "  vec3 tm = texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, -uTexelSize.y)).rgb;\n" +
            "  vec3 tr = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, -uTexelSize.y)).rgb;\n" +
            "  vec3 ml = texture2D(uTexSampler, vTexSamplingCoord + vec2(-uTexelSize.x, 0.0)).rgb;\n" +
            "  vec3 mr = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, 0.0)).rgb;\n" +
            "  vec3 bl = texture2D(uTexSampler, vTexSamplingCoord + vec2(-uTexelSize.x, uTexelSize.y)).rgb;\n" +
            "  vec3 bm = texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexelSize.y)).rgb;\n" +
            "  vec3 br = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, uTexelSize.y)).rgb;\n" +
            "  float lc = luma(center);\n" +
            "  float ltl = luma(tl), ltm = luma(tm), ltr = luma(tr);\n" +
            "  float lml = luma(ml), lmr = luma(mr);\n" +
            "  float lbl = luma(bl), lbm = luma(bm), lbr = luma(br);\n" +
            "  float minL = min(lc, min(min(min(ltl, ltm), min(ltr, lml)), min(min(lmr, lbl), min(lbm, lbr))));\n" +
            "  float maxL = max(lc, max(max(max(ltl, ltm), max(ltr, lml)), max(max(lmr, lbl), max(lbm, lbr))));\n" +
            "  float edge = max(0.0, maxL - minL);\n" +
            "  float w = 1.0 - exp(-edge * edge / SENSITIVITY) * STRENGTH;\n" +
            // 双边滤波（基于亮度差异的加权平均）
            "  float wc = 1.0;\n" +
            "  float wtl = exp(-abs(ltl - lc) / 0.1);\n" +
            "  float wtm = exp(-abs(ltm - lc) / 0.1);\n" +
            "  float wtr = exp(-abs(ltr - lc) / 0.1);\n" +
            "  float wml = exp(-abs(lml - lc) / 0.1);\n" +
            "  float wmr = exp(-abs(lmr - lc) / 0.1);\n" +
            "  float wbl = exp(-abs(lbl - lc) / 0.1);\n" +
            "  float wbm = exp(-abs(lbm - lc) / 0.1);\n" +
            "  float wbr = exp(-abs(lbr - lc) / 0.1);\n" +
            "  float totalW = wc + wtl + wtm + wtr + wml + wmr + wbl + wbm + wbr;\n" +
            "  vec3 result = (center * wc + tl * wtl + tm * wtm + tr * wtr + ml * wml + mr * wmr + bl * wbl + bm * wbm + br * wbr) / totalW;\n" +
            // 锐化（边缘增强）
            "  vec3 sharpened = center * 1.5 - (tl + tm + tr + ml + mr + bl + bm + br) / 8.0 * 0.5;\n" +
            "  result = mix(result, sharpened, w * SHARPEN);\n" +
            "  gl_FragColor = vec4(result, 1.0);\n" +
            "}\n";
    }

    private final GlProgram glProgram;
    private int width;
    private int height;

    public Anime4KShaderProgram(Context context, boolean useHighPrecisionColorComponents, int strength) throws VideoFrameProcessingException {
        super(useHighPrecisionColorComponents, 1);
        try {
            glProgram = new GlProgram(VERTEX_SHADER, buildFragmentShader(strength));
            glProgram.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4);
            float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
            glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
            glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
        } catch (Exception e) {
            com.github.catvod.utils.Logger.e("Anime4K Shader init failed", e);
            throw VideoFrameProcessingException.from(e);
        }
    }

    @Override
    public Size configure(int inputWidth, int inputHeight) {
        this.width = inputWidth;
        this.height = inputHeight;
        return new Size(inputWidth, inputHeight);
    }

    @Override
    public void drawFrame(int inputTexId, long presentationTimeUs) throws VideoFrameProcessingException {
        try {
            glProgram.use();
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0);
            glProgram.setFloatsUniform("uTexelSize", new float[]{
                    1.0f / Math.max(width, 1),
                    1.0f / Math.max(height, 1)
            });
            glProgram.bindAttributesAndUniforms();
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GlUtil.checkGlError();
        } catch (Exception e) {
            throw VideoFrameProcessingException.from(e, presentationTimeUs);
        }
    }

    @Override
    public void release() throws VideoFrameProcessingException {
        super.release();
        try {
            glProgram.delete();
        } catch (Exception e) {
            throw VideoFrameProcessingException.from(e);
        }
    }
}

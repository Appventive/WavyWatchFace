package com.appventive.wearexperiment;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

public class WaveAnimator {
    private WaveRenderer mWaveRenderer;

    private AnimatorSet mAnimatorSet;

    private ObjectAnimator waterLevelAnim;

    public WaveAnimator(WaveRenderer WaveRenderer) {
        mWaveRenderer = WaveRenderer;
        initAnimation();
    }

    public void start(float waterLevel) {
        waterLevelAnim.setFloatValues(0f, waterLevel);

        mWaveRenderer.setShowWave(true);
        if (mAnimatorSet != null) {
            mAnimatorSet.start();
        }
    }

    public void pause() {
        if (mAnimatorSet != null) {
            mAnimatorSet.pause();
        }
    }

    public void resume() {
        if (mAnimatorSet != null) {
            mAnimatorSet.resume();
        }
    }

    private void initAnimation() {
        List<Animator> animators = new ArrayList<>();

        // horizontal animation.
        // wave waves infinitely.
        ObjectAnimator waveShiftAnim = ObjectAnimator.ofFloat(
                mWaveRenderer, "waveShiftRatio", 0f, 1f);
        waveShiftAnim.setRepeatCount(ValueAnimator.INFINITE);
        waveShiftAnim.setDuration(1000);
        waveShiftAnim.setInterpolator(new LinearInterpolator());
        animators.add(waveShiftAnim);

        // vertical animation.
        waterLevelAnim = ObjectAnimator.ofFloat(
                mWaveRenderer, "waterLevelRatio", 0f, 0.55f);
        waterLevelAnim.setDuration(2000);
        waterLevelAnim.setInterpolator(new DecelerateInterpolator());
        animators.add(waterLevelAnim);

        // amplitude animation.
        // wave grows big then grows small, repeatedly
        ObjectAnimator amplitudeAnim = ObjectAnimator.ofFloat(
                mWaveRenderer, "amplitudeRatio", 0.01f, 0.05f);
        amplitudeAnim.setRepeatCount(ValueAnimator.INFINITE);
        amplitudeAnim.setRepeatMode(ValueAnimator.REVERSE);
        amplitudeAnim.setDuration(5000);
        amplitudeAnim.setInterpolator(new LinearInterpolator());
        animators.add(amplitudeAnim);

        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playTogether(animators);
    }

    public void cancel() {
        if (mAnimatorSet != null) {
            mAnimatorSet.end();
        }
    }
}

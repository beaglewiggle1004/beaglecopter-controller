package app.beaglecopter.controller;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

public class JoyStick {
    private static final String TAG = JoyStick.class.getSimpleName();

    public static final int STICK_NONE = 0;
    public static final int STICK_UP = 1;
    public static final int STICK_UPRIGHT = 2;
    public static final int STICK_RIGHT = 3;
    public static final int STICK_DOWNRIGHT = 4;
    public static final int STICK_DOWN = 5;
    public static final int STICK_DOWNLEFT = 6;
    public static final int STICK_LEFT = 7;
    public static final int STICK_UPLEFT = 8;

    private int STICK_ALPHA = 200;
    private int LAYOUT_ALPHA = 200;
    private int OFFSET = 0;

    private Context mContext;
    private ViewGroup mLayout;
    private LayoutParams params;
    private int stickWidth, stick_height;

    private int positionX = 0, positionY = 0, minDistance = 0;
    private float distance = 0, angle = 0;
    private int minX = 0, maxX = 0;
    private int minY = 0, maxY = 0;

    private boolean autoRecover = true;
    private boolean hasThrottle = false;

    private DrawCanvas draw;
    private Paint paint;
    private Bitmap stick;

    private boolean touchState = false;

    public JoyStick(Context context, ViewGroup layout, int stickResId, int minX, int maxX
            , int minY, int maxY) {
        mContext = context;

        stick = BitmapFactory.decodeResource(mContext.getResources(),
                stickResId);

        stickWidth = stick.getWidth();
        stick_height = stick.getHeight();

        draw = new DrawCanvas(mContext);
        paint = new Paint();
        mLayout = layout;
        params = mLayout.getLayoutParams();

        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    /**
     * @param autoRecover (default is false)
     */
    public void setAutoRecover(boolean autoRecover) {
        this.autoRecover = autoRecover;
    }

    public void setThrottle(boolean hasThrottle) {
        this.hasThrottle = hasThrottle;
    }

    public boolean hasThrottle() {
        return hasThrottle;
    }

    public void drawStick(MotionEvent event) {
        Log.d(TAG, "Pos x : " + event.getX() + " Width : " + params.width);

        positionX = (int) (event.getX());
        positionY = (int) (event.getY());
        int dX = positionX - (params.width / 2);
        int dY = positionY - (params.height / 2);
        distance = (float) Math.sqrt(Math.pow(dX, 2) + Math.pow(dY, 2));
        angle = (float) calAngle(dX, dY);

        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            if (distance <= (params.width / 2) - OFFSET) {
                draw.position(event.getX(), event.getY());
                draw();
                touchState = true;
            }
        } else if (action == MotionEvent.ACTION_MOVE && touchState) {
            if (distance <= (params.width / 2) - OFFSET) {
                draw.position(event.getX(), event.getY());
                draw();
            } else if (distance > (params.width / 2) - OFFSET) {
                float x = (float) (Math.cos(Math.toRadians(calAngle(dX, dY))) * ((params.width / 2) - OFFSET));
                float y = (float) (Math.sin(Math.toRadians(calAngle(dX, dY))) * ((params.height / 2) - OFFSET));
                x += (params.width / 2);
                y += (params.height / 2);
                draw.position(x, y);
                draw();
            } else {
                selfCentered();
            }
        } else if (action == MotionEvent.ACTION_UP) {
            touchState = false;
            selfCentered();
        }
    }

    protected void selfCentered() {
        if (autoRecover) {
            positionX = params.width / 2;
            distance = 0;
            angle = 0;

            if (hasThrottle) {
                // Throttle valve should be kept position
                draw.position(params.width / 2, positionY);
            } else {
                positionY = params.height / 2;
                draw.position(params.width / 2, params.height / 2);
            }
        }
        draw();
    }

    private int map(int value, int inMin, int inMax, int outMin, int outMax) {
        int mappedVal = (value - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
        return (mappedVal <= outMin ? outMin : (mappedVal >= outMax ? outMax : mappedVal));
    }

    public int[] getPosition() {
        if (distance > minDistance && touchState) {
            return new int[]{positionX, positionY};
        }
        return new int[]{0, 0};
    }

    public int getX() {
        return map(positionX, 0, params.width, minX, maxX);
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getY() {
        return map(params.height - positionY, 0, params.height, minY, maxY);
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public float getAngle() {
        return angle;
    }

    public float getDistance() {
        return distance;
    }

    public void setMinimumDistance(int minDistance) {
        this.minDistance = minDistance;
    }

    public int getMinimumDistance() {
        return minDistance;
    }

    public int get8Direction() {
        if (distance > minDistance && touchState) {
            if (angle >= 247.5 && angle < 292.5) {
                return STICK_UP;
            } else if (angle >= 292.5 && angle < 337.5) {
                return STICK_UPRIGHT;
            } else if (angle >= 337.5 || angle < 22.5) {
                return STICK_RIGHT;
            } else if (angle >= 22.5 && angle < 67.5) {
                return STICK_DOWNRIGHT;
            } else if (angle >= 67.5 && angle < 112.5) {
                return STICK_DOWN;
            } else if (angle >= 112.5 && angle < 157.5) {
                return STICK_DOWNLEFT;
            } else if (angle >= 157.5 && angle < 202.5) {
                return STICK_LEFT;
            } else if (angle >= 202.5 && angle < 247.5) {
                return STICK_UPLEFT;
            }
        } else if (distance <= minDistance && touchState) {
            return STICK_NONE;
        }
        return 0;
    }

    public int get4Direction() {
        if (distance > minDistance && touchState) {
            if (angle >= 225 && angle < 315) {
                return STICK_UP;
            } else if (angle >= 315 || angle < 45) {
                return STICK_RIGHT;
            } else if (angle >= 45 && angle < 135) {
                return STICK_DOWN;
            } else if (angle >= 135 && angle < 225) {
                return STICK_LEFT;
            }
        } else if (distance <= minDistance && touchState) {
            return STICK_NONE;
        }
        return 0;
    }

    public void setOffset(int offset) {
        OFFSET = offset;
    }

    public int getOffset() {
        return OFFSET;
    }

    public void setStickAlpha(int alpha) {
        STICK_ALPHA = alpha;
        paint.setAlpha(alpha);
    }

    public int getStickAlpha() {
        return STICK_ALPHA;
    }

    public void setLayoutAlpha(int alpha) {
        LAYOUT_ALPHA = alpha;
        mLayout.getBackground().setAlpha(alpha);
    }

    public int getLayoutAlpha() {
        return LAYOUT_ALPHA;
    }

    public void setStickSize(int width, int height) {
        stick = Bitmap.createScaledBitmap(stick, width, height, false);
        stickWidth = stick.getWidth();
        stick_height = stick.getHeight();
    }

    public void setStickWidth(int width) {
        stick = Bitmap.createScaledBitmap(stick, width, stick_height, false);
        stickWidth = stick.getWidth();
    }

    public void setStickHeight(int height) {
        stick = Bitmap.createScaledBitmap(stick, stickWidth, height, false);
        stick_height = stick.getHeight();
    }

    public int getStickWidth() {
        return stickWidth;
    }

    public int getStickHeight() {
        return stick_height;
    }

    public void setLayoutSize(int width, int height) {
        params.width = width;
        params.height = height;
    }

    public int getLayoutWidth() {
        return params.width;
    }

    public int getLayoutHeight() {
        return params.height;
    }

    private double calAngle(float x, float y) {
        if (x >= 0 && y >= 0)
            return Math.toDegrees(Math.atan(y / x));
        else if (x < 0 && y >= 0)
            return Math.toDegrees(Math.atan(y / x)) + 180;
        else if (x < 0 && y < 0)
            return Math.toDegrees(Math.atan(y / x)) + 180;
        else if (x >= 0 && y < 0)
            return Math.toDegrees(Math.atan(y / x)) + 360;
        return 0;
    }

    private void draw() {
        try {
            mLayout.removeView(draw);
        } catch (Exception e) {
        }
        mLayout.addView(draw);
    }

    private class DrawCanvas extends View {
        float x, y;

        private DrawCanvas(Context mContext) {
            super(mContext);
        }

        public void onDraw(Canvas canvas) {
            canvas.drawBitmap(stick, x, y, paint);
        }

        private void position(float posX, float posY) {
            x = posX - (stickWidth / 2);
            y = posY - (stick_height / 2);
        }
    }
}

package com.intuit.karate.ui;

import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import com.intuit.karate.core.AdhocCoverageTool;

/**
 * {@link DragResizer} can be used to add mouse listeners to a {@link Region}
 * and make it moveable and resizable by the user by clicking and dragging the
 * border in the same way as a window, or clicking within the window to move it
 * around.
 * <p>
 * Height and width resizing is implemented, from the sides and corners.
 * Dragging of the region is also optionally available, and the movement and
 * resizing can be constrained within the bounds of the parent.
 * <p>
 * Usage:
 * <pre>DragResizer.makeResizable(myAnchorPane, true, true, true, true);</pre> makes the
 * region resizable for hight and width and moveable, but only within the bounds of the parent.
 * <p>
 * Builds on the modifications to the original version by
 * Geoff Capper.
 * <p>
 *
 */
public class DragResizer {

    /**
     * Enum containing the zones that we can drag around.
     */
    enum Zone {
        NONE, N, NE, E, SE, S, SW, W, NW, C
    }

    /**
     * The margin around the control that a user can click in to start resizing
     * the region.
     */
    private  final int RESIZE_MARGIN = 5;

    /**
     * How small can we go?
     */
    private  final int MIN_SIZE = 10;

    private Region region;

    private double y;

    private double x;

    private boolean initMinHeight;

    private boolean initMinWidth;

    private Zone zone;

    private boolean dragging;

    /**
     * Whether the sizing and movement of the region is constrained within the
     * bounds of the parent.
     */
    private boolean constrainToParent;

    /**
     * Whether dragging of the region is allowed.
     */
    private boolean allowMove;

    /**
     * Whether resizing of height is allowed.
     */
    private boolean allowHeightResize;

    /**
     * Whether resizing of width is allowed.
     */
    private boolean allowWidthResize;

    private DragResizer(Region aRegion, boolean allowMove, boolean constrainToParent, boolean allowHeightResize, boolean allowWidthResize) {
        region = aRegion;
        this.constrainToParent = constrainToParent;
        this.allowMove = allowMove;
        this.allowHeightResize = allowHeightResize;
        this.allowWidthResize = allowWidthResize;
    }


    /**
     * Makes the region resizable, and optionally moveable, and constrained
     * within the bounds of the parent.
     *
     * @param region
     * @param allowMove Allow a click in the centre of the region to start
     * dragging it around.
     * @param constrainToParent Prevent movement and/or resizing outside the
     * @param allowHeightResize if set to true makes component height resizeAble
     * @param allowWidthResize if set to true makes component width resizeAble
     * parent.
     */
    public static void makeResizable(Region region, boolean allowMove, boolean constrainToParent, boolean allowHeightResize, boolean allowWidthResize) {
        final DragResizer resizer = new DragResizer(region, allowMove, constrainToParent, allowHeightResize, allowWidthResize);

        region.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                resizer.mousePressed(event);
            }
        });
        region.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                resizer.mouseDragged(event);
            }
        });
        region.setOnMouseMoved(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                resizer.mouseOver(event);
            }
        });
        region.setOnMouseReleased(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                resizer.mouseReleased(event);
            }
        });
    }

    protected void mouseReleased(MouseEvent event) {
        dragging = false;
        region.setCursor(Cursor.DEFAULT);
    }

    protected void mouseOver(MouseEvent event) {
        if (isInDraggableZone(event) || dragging) {
            switch (zone) {
                case N: {
                    region.setCursor(Cursor.N_RESIZE);
                    break;
                }
                case NE: {
                    region.setCursor(Cursor.NE_RESIZE);
                    break;
                }
                case E: {
                    region.setCursor(Cursor.E_RESIZE);
                    break;
                }
                case SE: {
                    region.setCursor(Cursor.SE_RESIZE);
                    break;
                }
                case S: {
                    region.setCursor(Cursor.S_RESIZE);
                    break;
                }
                case SW: {
                    region.setCursor(Cursor.SW_RESIZE);
                    break;
                }
                case W: {
                    region.setCursor(Cursor.W_RESIZE);
                    break;
                }
                case NW: {
                    region.setCursor(Cursor.NW_RESIZE);
                    break;
                }
                case C: {
                    region.setCursor(Cursor.MOVE);
                    break;
                }
            }

        } else {
            region.setCursor(Cursor.DEFAULT);
        }
    }

    protected boolean isInDraggableZone(MouseEvent event) {
        zone = Zone.NONE;
        if(allowWidthResize) {
            if ((event.getY() < RESIZE_MARGIN) && (event.getX() < RESIZE_MARGIN)) {
                zone = Zone.NW;
            } else if ((event.getY() < RESIZE_MARGIN) && (event.getX() > (region.getWidth() - RESIZE_MARGIN))) {
                zone = Zone.NE;
            } else if ((event.getY() > (region.getHeight() - RESIZE_MARGIN)) && (event.getX() > (region.getWidth() - RESIZE_MARGIN))) {
                zone = Zone.SE;
            } else if ((event.getY() > (region.getHeight() - RESIZE_MARGIN)) && (event.getX() < RESIZE_MARGIN)) {
                zone = Zone.SW;
            } else if (event.getX() < RESIZE_MARGIN) {
                zone = Zone.W;
            } else if (event.getX() > (region.getWidth() - RESIZE_MARGIN)) {
                zone = Zone.E;
            }
        } else if (allowHeightResize) {
            if (event.getY() > (region.getHeight() - RESIZE_MARGIN)) {
                zone = Zone.S;
            } else if (event.getY() < RESIZE_MARGIN) {
                zone = Zone.N;
            }
        } else if (allowMove) {
                zone = Zone.C;
        }
        return !Zone.NONE.equals(zone);

    }

    protected void mouseDragged(MouseEvent event) {
		AdhocCoverageTool.m.get("mouseDragged")[0] = true;
        if (!dragging) {
			AdhocCoverageTool.m.get("mouseDragged")[1] = true;
            return;
        }

        double deltaY = allowHeightResize ? event.getSceneY() - y : 0;
        double deltaX = allowWidthResize ? event.getSceneX() - x : 0;

        double originY = region.getLayoutY();
        double originX = region.getLayoutX();

        double newHeight = region.getMinHeight();
        double newWidth = region.getMinWidth();

        switch (zone) {
            case N: {
				AdhocCoverageTool.m.get("mouseDragged")[2] = true;
                originY += deltaY;
                newHeight -= deltaY;
                break;
            }
            case NE: {
				AdhocCoverageTool.m.get("mouseDragged")[3] = true;
                originY += deltaY;
                newHeight -= deltaY;
                newWidth += deltaX;
                break;
            }
            case E: {
				AdhocCoverageTool.m.get("mouseDragged")[4] = true;
                newWidth += deltaX;
                break;
            }
            case SE: {
				AdhocCoverageTool.m.get("mouseDragged")[5] = true;
                newHeight += deltaY;
                newWidth += deltaX;
                break;
            }
            case S: {
				AdhocCoverageTool.m.get("mouseDragged")[6] = true;
                newHeight += deltaY;
                break;
            }
            case SW: {
				AdhocCoverageTool.m.get("mouseDragged")[7] = true;
                originX += deltaX;
                newHeight += deltaY;
                newWidth -= deltaX;
                break;
            }
            case W: {
				AdhocCoverageTool.m.get("mouseDragged")[8] = true;
                originX += deltaX;
                newWidth -= deltaX;
                break;
            }
            case NW: {
				AdhocCoverageTool.m.get("mouseDragged")[9] = true;
                originY += deltaY;
                originX += deltaX;
                newWidth -= deltaX;
                newHeight -= deltaY;
                break;
            }
            case C: {
				AdhocCoverageTool.m.get("mouseDragged")[10] = true;
                originY += deltaY;
                originX += deltaX;
                break;
            }
        }

        if (constrainToParent) {
			AdhocCoverageTool.m.get("mouseDragged")[11] = true;

            if (originX < 0) {
				AdhocCoverageTool.m.get("mouseDragged")[12] = true;
                if (!Zone.C.equals(zone)) {
					AdhocCoverageTool.m.get("mouseDragged")[13] = true;
                    newWidth -= Math.abs(originX);
                }
                originX = 0;
            }
            if (originY < 0) {
				AdhocCoverageTool.m.get("mouseDragged")[14] = true;
                if (!Zone.C.equals(zone)) {
					AdhocCoverageTool.m.get("mouseDragged")[15] = true;
                    newHeight -= Math.abs(originY);
                }
                originY = 0;
            }

            if (Zone.C.equals(zone)) {
				AdhocCoverageTool.m.get("mouseDragged")[16] = true;
                if ((newHeight + originY) > region.getParent().getBoundsInLocal().getHeight()) {
					AdhocCoverageTool.m.get("mouseDragged")[17] = true;
                    originY = region.getParent().getBoundsInLocal().getHeight() - newHeight;
                }
                if ((newWidth + originX) > region.getParent().getBoundsInLocal().getWidth()) {
					AdhocCoverageTool.m.get("mouseDragged")[18] = true;
                    originX = region.getParent().getBoundsInLocal().getWidth() - newWidth;
                }
            } else {
				AdhocCoverageTool.m.get("mouseDragged")[19] = true;
                if ((newHeight + originY) > region.getParent().getBoundsInLocal().getHeight()) {
					AdhocCoverageTool.m.get("mouseDragged")[20] = true;
                    newHeight = region.getParent().getBoundsInLocal().getHeight() - originY;
                }
                if ((newWidth + originX) > region.getParent().getBoundsInLocal().getWidth()) {
					AdhocCoverageTool.m.get("mouseDragged")[21] = true;
                    newWidth = region.getParent().getBoundsInLocal().getWidth() - originX;
                }
            }
        }
        if (newWidth < MIN_SIZE) {
			AdhocCoverageTool.m.get("mouseDragged")[22] = true;
            newWidth = MIN_SIZE;
        }
        if (newHeight < MIN_SIZE) {
			AdhocCoverageTool.m.get("mouseDragged")[23] = true;
            newHeight = MIN_SIZE;
        }

        if (!Zone.C.equals(zone)) {
			AdhocCoverageTool.m.get("mouseDragged")[24] = true;
            // need to set Pref Height/Width otherwise they act as minima.
            if(allowHeightResize) {
				AdhocCoverageTool.m.get("mouseDragged")[25] = true;
                region.setMinHeight(newHeight);
                region.setPrefHeight(newHeight);
            }
            if(allowWidthResize) {
				AdhocCoverageTool.m.get("mouseDragged")[26] = true;
                region.setMinWidth(newWidth);
                region.setPrefWidth(newWidth);
            }
        }
        if(allowMove) {
			AdhocCoverageTool.m.get("mouseDragged")[27] = true;
            region.relocate(originX, originY);
        }

		if (allowHeightResize) {
			AdhocCoverageTool.m.get("mouseDragged")[28] = true;
			y = event.getSceneY();
		} else {
			AdhocCoverageTool.m.get("mouseDragged")[29] = true;
			y = y;
		}

		if (allowWidthResize) {
			AdhocCoverageTool.m.get("mouseDragged")[30] = true;
			x = event.getSceneX();
		} else {
			AdhocCoverageTool.m.get("mouseDragged")[31] = true;
			x = x;
		}
    }

    protected void mousePressed(MouseEvent event) {

        // ignore clicks outside of the draggable margin
        if (!isInDraggableZone(event)) {
            return;
        }

        dragging = true;

        // make sure that the minimum height is set to the current height once,
        // setting a min height that is smaller than the current height will
        // have no effect
        if (!initMinHeight) {
            region.setMinHeight(region.getHeight());
            initMinHeight = true;
        }

        y = event.getSceneY();

        if (!initMinWidth) {
            region.setMinWidth(region.getWidth());
            initMinWidth = true;
        }

        x = event.getSceneX();
    }

}

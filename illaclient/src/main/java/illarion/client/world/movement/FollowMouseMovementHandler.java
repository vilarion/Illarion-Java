/*
 * This file is part of the Illarion project.
 *
 * Copyright © 2014 - Illarion e.V.
 *
 * Illarion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Illarion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package illarion.client.world.movement;

import illarion.client.IllaClient;
import illarion.client.world.CharMovementMode;
import illarion.client.world.MapDimensions;
import illarion.common.config.ConfigChangedEvent;
import illarion.common.types.Location;
import illarion.common.util.FastMath;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventTopicSubscriber;
import org.illarion.engine.input.Input;
import org.illarion.engine.input.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * This is the movement handler that takes care for the follow mouse movement system. As long as this is engaged moves
 * following the mouse will be plotted and send to the movement handler.
 *
 * @author Martin Karing &lt;nitram@illarion.org&gt;
 */
class FollowMouseMovementHandler extends AbstractMovementHandler implements MouseMovementHandler {
    @Nonnull
    private static final Logger LOGGER = LoggerFactory.getLogger(FollowMouseMovementHandler.class);
    /**
     * This value is the relation of the distance from the character location to the location of the cursor to the
     * plain x or y offset. In case the relation is smaller or equal to this the character will move straight
     * horizontal or vertical on the screen. Else it will move diagonal.
     */
    private static final double MOUSE_ANGLE = StrictMath.cos(Math.PI / Location.DIR_MOVE8);

    @Nonnull
    private final Input input;

    /**
     * The last reported X coordinate of the mouse.
     */
    private int lastMouseX;

    /**
     * The last reported Y coordinate of the mouse.
     */
    private int lastMouseY;

    /**
     * Always run when moving with the mouse.
     */
    private boolean mouseFollowAutoRun;

    private CharMovementMode currentMovementMode;
    private int walkTowardsDir;

    FollowMouseMovementHandler(@Nonnull Movement movement, @Nonnull Input input) {
        super(movement);
        this.input = input;
        lastMouseX = -1;
        lastMouseY = -1;

        mouseFollowAutoRun = IllaClient.getCfg().getBoolean("mouseFollowAutoRun");

        AnnotationProcessor.process(this);
    }

    @Nonnull
    @Override
    public StepData getNextStep() {
        calculateMove();
        return new DefaultStepData(currentMovementMode, walkTowardsDir);
    }

    @Override
    public void handleMouse(int x, int y) {
        lastMouseX = x;
        lastMouseY = y;
    }

    private void calculateMove() {
        MapDimensions mapDimensions = MapDimensions.getInstance();
        int xOffset = lastMouseX - (mapDimensions.getOnScreenWidth() / 2);
        int yOffset = -(lastMouseY - (mapDimensions.getOnScreenHeight() / 2));
        int distance = FastMath.sqrt((xOffset * xOffset) + (yOffset * yOffset));

        if (distance <= 5) {
            return;
        }

        float relXOffset = (float) xOffset / distance;
        float relYOffset = (float) yOffset / distance;

        //noinspection IfStatementWithTooManyBranches
        currentMovementMode = getWalkTowardsMode(distance);

        //noinspection IfStatementWithTooManyBranches
        if (relXOffset > MOUSE_ANGLE) {
            walkTowardsDir = Location.DIR_SOUTHEAST;
        } else if (relXOffset < -MOUSE_ANGLE) {
            walkTowardsDir = Location.DIR_NORTHWEST;
        } else if (relYOffset > MOUSE_ANGLE) {
            walkTowardsDir = Location.DIR_NORTHEAST;
        } else if (relYOffset < -MOUSE_ANGLE) {
            walkTowardsDir = Location.DIR_SOUTHWEST;
        } else if ((xOffset > 0) && (yOffset > 0)) {
            walkTowardsDir = Location.DIR_EAST;
        } else if ((xOffset > 0) && (yOffset < 0)) {
            walkTowardsDir = Location.DIR_SOUTH;
        } else if ((xOffset < 0) && (yOffset < 0)) {
            walkTowardsDir = Location.DIR_WEST;
        } else if ((xOffset < 0) && (yOffset > 0)) {
            walkTowardsDir = Location.DIR_NORTH;
        }
    }

    private CharMovementMode getWalkTowardsMode(int distance) {
        if (input.isAnyKeyDown(Key.LeftShift, Key.RightShift)) {
            return CharMovementMode.None;
        }

        if (mouseFollowAutoRun) {
            return getMovement().getDefaultMovementMode();
        }

        CharMovementMode mode = CharMovementMode.Walk;
        if (distance > 200) {
            mode = CharMovementMode.Run;
        } else if (distance < 30) {
            mode = CharMovementMode.None;
        }
        return mode;
    }

    @EventTopicSubscriber(topic = "mouseFollowAutoRun")
    private void mouseFollowAutoRunChanged(@Nonnull String topic, @Nonnull ConfigChangedEvent configChanged) {
        mouseFollowAutoRun = configChanged.getConfig().getBoolean("mouseFollowAutoRun");
    }

    @Override
    public String toString() {
        return "Follow mouse movement handler";
    }
}

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
package illarion.client.util.pathfinding;

import illarion.common.types.Location;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedList;

/**
 * A path created by the path finder that stores the path nodes.
 */
public final class Path {

    /**
     * List if the path nodes that create this path.
     */
    private final LinkedList<PathNode> path = new LinkedList<>();

    @Nullable
    private final Location destination;

    /**
     * Default constructor for a new path.
     */
    public Path(@Nonnull Iterable<PathNode> nodes) {
        for (PathNode node : nodes) {
            path.addLast(node);
        }
        if (path.isEmpty()) {
            destination = null;
        } else {
            destination = new Location(path.getLast().getLocation());
        }
    }

    /**
     * Get the destination location of the path. So the location where the path
     * ends.
     *
     * @return the destination of the path
     */
    @Nullable
    public Location getDestination() {
        return destination;
    }

    /**
     * Get the next step of this path and remove it from the list. This function
     * returns always the first value entry of the path node list and removed it
     * from the list then.
     *
     * @return the next path node of this path
     */
    @Nullable
    public PathNode nextStep() {
        PathNode node = null;
        if (!path.isEmpty()) {
            node = path.removeFirst();
        }
        return node;
    }

    /**
     * Check if the path is now empty.
     *
     * @return {@code true} in case the path is empty
     */
    public boolean isEmpty() {
        return path.isEmpty();
    }

    /**
     * Create a string representation of this path. Containing the value of the
     * path and the path itself.
     *
     * @return the string that defines the path
     */
    @Nonnull
    @Override
    @SuppressWarnings("nls")
    public String toString() {
        if (path.isEmpty()) {
            return "Empty path.";
        }
        return "Path from " + path.getFirst().getLocation() + " to " + path.getLast().getLocation() +
                " with " + path.size() + " steps";
    }
}

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
package org.illarion.engine.graphic.effects;

/**
 * This is the scene effect that shows the fog over the scene.
 *
 * @author Martin Karing &lt;nitram@illarion.org&gt;
 */
public interface FogEffect extends SceneEffect {
    /**
     * Set the density of the fog.
     *
     * @param density the fog density, values are capped from {@code 0.f} to {@code 1.f}
     */
    void setDensity(float density);
}

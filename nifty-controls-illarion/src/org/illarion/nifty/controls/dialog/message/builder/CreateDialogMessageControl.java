/*
 * This file is part of the Illarion Nifty-GUI Controls.
 *
 * Copyright © 2012 - Illarion e.V.
 *
 * The Illarion Nifty-GUI Controls is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Illarion Nifty-GUI Controls is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Illarion Nifty-GUI Controls.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.illarion.nifty.controls.dialog.message.builder;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyIdCreator;
import de.lessvoid.nifty.controls.dynamic.attributes.ControlAttributes;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.loaderv2.types.ControlType;
import de.lessvoid.nifty.loaderv2.types.ElementType;
import de.lessvoid.nifty.screen.Screen;

import org.illarion.nifty.controls.DialogMessage;

/**
 * Created by IntelliJ IDEA. User: Martin Karing Date: 17.03.12 Time: 20:01 To change this template use File | Settings
 * | File Templates.
 */
public class CreateDialogMessageControl
        extends ControlAttributes {
    /**
     * The identifier string of the message dialog control.
     */
    static final String NAME = "dialog-message";

    /**
     * Create a new inventory slot with a automatically generated ID.
     */
    public CreateDialogMessageControl() {
        setAutoId(NiftyIdCreator.generate());
        setName(NAME);
    }

    /**
     * Create a new message dialog with a user defined ID.
     *
     * @param id the ID of the new control
     */
    public CreateDialogMessageControl(final String id) {
        setId(id);
        setName(NAME);
    }

    /**
     * Create the dialog message
     *
     * @param nifty the instance of the Nifty-GUI that will display the dialog
     * @param screen the screen this dialog will be a part of
     * @param parent the parent element of this dialog
     * @return the newly created message dialog
     */
    public DialogMessage create(final Nifty nifty, final Screen screen, final Element parent) {
        nifty.addControl(screen, parent, getStandardControl());
        nifty.addControlsWithoutStartScreen();
        return parent.findNiftyControl(attributes.get("id"), DialogMessage.class);
    }

    /**
     * Create the element type of this dialog.
     *
     * @return the element type of the dialog
     */
    @Override
    public ElementType createType() {
        return new ControlType(attributes);
    }
}
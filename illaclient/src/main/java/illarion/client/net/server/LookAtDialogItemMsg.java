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
package illarion.client.net.server;

import illarion.client.gui.Tooltip;
import illarion.client.net.CommandList;
import illarion.client.net.annotations.ReplyMessage;
import illarion.client.world.World;
import illarion.common.net.NetCommReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Servermessage: Look at description of a tile ({@link CommandList#MSG_LOOKAT_DIALOG_ITEM}).
 *
 * @author Martin Karing &lt;nitram@illarion.org&gt;
 */
@ReplyMessage(replyId = CommandList.MSG_LOOKAT_DIALOG_ITEM)
public final class LookAtDialogItemMsg extends AbstractGuiMsg {
    private int dialogId;
    private int type;
    private int slotId;
    private int secondarySlotId;
    private Tooltip tooltip;

    private static final Logger LOGGER = LoggerFactory.getLogger(LookAtDialogItemMsg.class);

    /**
     * Decode the tile look at text data the receiver got and prepare it for the execution.
     *
     * @param reader the receiver that got the data from the server that needs to be decoded
     * @throws IOException thrown in case there was not enough data received to decode the full message
     */
    @Override
    public void decode(@Nonnull final NetCommReader reader) throws IOException {
        dialogId = reader.readInt();
        type = reader.readUByte();
        switch (type) {
            case 0:
                slotId = reader.readUByte();
                break;
            case 1:
                slotId = reader.readUByte();
                secondarySlotId = reader.readUByte();
                break;
            default:
                LOGGER.error("Illegal type ID: " + Integer.toString(type));
                return;
        }

        tooltip = new Tooltip(reader);
    }

    /**
     * Execute the tile look at text message and send the decoded data to the rest of the client.
     *
     * @return true if the execution is done, false if it shall be called again
     */
    @Override
    public boolean executeUpdate() {
        switch (type) {
            case 0:
                World.getGameGui().getDialogCraftingGui().showCraftItemTooltip(dialogId, slotId, tooltip);
                break;
            case 1:
                World.getGameGui().getDialogCraftingGui()
                        .showCraftIngredientTooltip(dialogId, slotId, secondarySlotId, tooltip);
                break;
            default:
                LOGGER.error("Illegal type ID " + Integer.toString(type));
        }

        return true;
    }

    /**
     * Get the data of this tile look at text message as string.
     *
     * @return the string that contains the values that were decoded for this message
     */
    @Nonnull
    @SuppressWarnings("nls")
    @Override
    public String toString() {
        return toString("Type: " + type + " Slot: " + slotId + " secondary Slot: " + secondarySlotId);
    }
}

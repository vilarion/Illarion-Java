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
package illarion.client.gui.controller.game;

import de.lessvoid.nifty.EndNotify;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.builder.ControlBuilder;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.tools.SizeValue;
import illarion.client.graphics.FontLoader;
import illarion.client.gui.DialogCraftingGui;
import illarion.client.gui.DialogInputGui;
import illarion.client.gui.DialogMessageGui;
import illarion.client.gui.Tooltip;
import illarion.client.gui.events.TooltipsRemovedEvent;
import illarion.client.gui.util.NiftyCraftingCategory;
import illarion.client.gui.util.NiftyCraftingItem;
import illarion.client.gui.util.NiftyMerchantItem;
import illarion.client.gui.util.NiftySelectItem;
import illarion.client.net.client.*;
import illarion.client.net.server.events.*;
import illarion.client.util.GlobalExecutorService;
import illarion.client.util.UpdateTask;
import illarion.client.world.World;
import illarion.client.world.events.CloseDialogEvent;
import illarion.client.world.items.CraftingItem;
import illarion.client.world.items.MerchantList;
import illarion.client.world.items.SelectionItem;
import illarion.common.types.ItemCount;
import illarion.common.types.Rectangle;
import org.bushe.swing.event.EventBus;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.illarion.engine.GameContainer;
import org.illarion.engine.graphic.Font;
import org.illarion.engine.input.Button;
import org.illarion.engine.input.Input;
import org.illarion.engine.input.Key;
import org.illarion.nifty.controls.*;
import org.illarion.nifty.controls.dialog.input.builder.DialogInputBuilder;
import org.illarion.nifty.controls.dialog.message.builder.DialogMessageBuilder;
import org.illarion.nifty.controls.dialog.select.builder.DialogSelectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is the dialog handler that takes care for receiving events to show dialogs. It opens and maintains those
 * dialogs and notifies the server in case a dialog is closed.
 *
 * @author Martin Karing &lt;nitram@illarion.org&gt;
 */
public final class DialogHandler
        implements DialogCraftingGui, DialogMessageGui, DialogInputGui, ScreenController, UpdatableHandler {
    private static class BuildWrapper {
        private final ControlBuilder builder;
        private final Element parent;

        @Nullable
        private final PostBuildTask task;

        BuildWrapper(
                ControlBuilder builder, Element parent, @Nullable PostBuildTask task) {
            this.builder = builder;
            this.parent = parent;
            this.task = task;
        }

        public void executeTask(Element createdElement) {
            if (task != null) {
                task.run(createdElement);
            }
        }

        public ControlBuilder getBuilder() {
            return builder;
        }

        public Element getParent() {
            return parent;
        }
    }

    private interface PostBuildTask {
        void run(Element createdElement);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DialogHandler.class);

    private static final Pattern dialogNamePattern = Pattern.compile("([a-z]+)Dialog([0-9]+)");
    /**
     * The input control that is used in this dialog handler.
     */
    private final Input input;

    @Nullable
    private DialogMerchant merchantDialog;
    private DialogCrafting craftingDialog;
    private boolean openCraftDialog;

    @Nonnull
    private final Queue<BuildWrapper> builders;
    @Nonnull
    private final Queue<CloseDialogEvent> closers;
    private Nifty nifty;
    private Screen screen;
    private final NumberSelectPopupHandler numberSelect;
    private final TooltipHandler tooltipHandler;

    private int lastCraftingTooltip = -2;

    public DialogHandler(
            Input input, NumberSelectPopupHandler numberSelectPopupHandler, TooltipHandler tooltipHandler) {
        this.input = input;
        this.tooltipHandler = tooltipHandler;
        builders = new ConcurrentLinkedQueue<>();
        closers = new ConcurrentLinkedQueue<>();
        numberSelect = numberSelectPopupHandler;
    }

    @EventSubscriber
    public void handleCloseDialogEvent(CloseDialogEvent event) {
        closers.offer(event);
    }

    @EventSubscriber
    public void handleCraftingDialogEvent(@Nonnull final DialogCraftingReceivedEvent event) {
        GlobalExecutorService.getService().submit(new Runnable() {
            @Override
            public void run() {
                showCraftingDialog(event);
            }
        });
    }

    private void showCraftingDialog(@Nonnull final DialogCraftingReceivedEvent event) {
        if ((event.getRequestId() == craftingDialog.getDialogId()) && openCraftDialog) {
            World.getUpdateTaskManager().addTask(new UpdateTask() {
                @Override
                public void onUpdateGame(@Nonnull GameContainer container, int delta) {
                    CraftingItemEntry selectedItem = craftingDialog.getSelectedCraftingItem();

                    int selectedIndex;
                    if (selectedItem != null) {
                        selectedIndex = selectedItem.getItemIndex();
                    } else {
                        selectedIndex = 0;
                    }

                    craftingDialog.clearItemList();
                    addCraftingItemsToDialog(event, craftingDialog);

                    if (selectedItem != null) {
                        craftingDialog.selectItemByItemIndex(selectedIndex);
                    }
                }
            });
        } else {
            closeCraftingDialog(craftingDialog.getDialogId());
            World.getUpdateTaskManager().addTask(new UpdateTask() {
                @Override
                public void onUpdateGame(@Nonnull GameContainer container, int delta) {
                    craftingDialog.setDialogId(event.getRequestId());
                    craftingDialog.clearItemList();
                    addCraftingItemsToDialog(event, craftingDialog);
                    craftingDialog.setProgress(0.f);
                    craftingDialog.selectItemByItemIndex(0);
                    craftingDialog.setAmount(1);
                    craftingDialog.getElement().show(new EndNotify() {
                        @Override
                        public void perform() {
                            craftingDialog.moveToFront();
                            craftingDialog.selectItemByItemIndex(0);
                        }
                    });
                    openCraftDialog = true;
                }
            });
        }
    }

    private void addCraftingItemsToDialog(
            @Nonnull DialogCraftingReceivedEvent event, @Nonnull DialogCrafting dialog) {
        NiftyCraftingCategory[] categories = new NiftyCraftingCategory[event.getGroupCount()];
        for (int i = 0; i < event.getGroupCount(); i++) {
            categories[i] = new NiftyCraftingCategory(event.getGroupTitle(i));
        }

        boolean addedToUnknown = false;
        NiftyCraftingCategory unknownCat = new NiftyCraftingCategory("not assigned");

        for (int i = 0; i < event.getCraftingItemCount(); i++) {
            CraftingItem item = event.getCraftingItem(i);
            int groupId = item.getGroup();
            if ((groupId < 0) || (groupId >= categories.length)) {
                addedToUnknown = true;
                unknownCat.addChild(new NiftyCraftingItem(nifty, i, item));
                LOGGER.warn("Crafting item with illegal group received: {}", Integer.toString(groupId));
            } else {
                categories[item.getGroup()].addChild(new NiftyCraftingItem(nifty, i, item));
            }
        }

        dialog.addCraftingItems(categories);
        if (addedToUnknown) {
            dialog.addCraftingItems(unknownCat);
        }
    }

    @EventSubscriber
    public void handleDialogCraftingUpdateAbortedEvent(DialogCraftingUpdateAbortedReceivedEvent event) {
        if (craftingDialog == null) {
            return;
        }

        craftingDialog.setProgress(0.f);
    }

    @EventSubscriber
    public void handleDialogCraftingUpdateCompletedEvent(DialogCraftingUpdateCompletedReceivedEvent event) {
        if (craftingDialog == null) {
            return;
        }

        craftingDialog.setProgress(0.f);
        craftingDialog.setAmount(craftingDialog.getAmount() - 1);
    }

    @EventSubscriber
    public void handleDialogCraftingUpdateStartEvent(@Nonnull DialogCraftingUpdateStartReceivedEvent event) {
        if (craftingDialog == null) {
            return;
        }

        craftingDialog.startProgress((double) event.getRequiredTime() / 10.0);
    }

    @EventSubscriber
    public void handleMerchantDialogEvent(@Nonnull DialogMerchantReceivedEvent event) {
        showMerchantDialog(event);
    }

    private void showMerchantDialog(@Nonnull final DialogMerchantReceivedEvent event) {
        World.getUpdateTaskManager().addTask(new UpdateTask() {
            @Override
            public void onUpdateGame(@Nonnull GameContainer container, int delta) {
                merchantDialog.clearItems();
                merchantDialog.setDialogId(event.getId());
                merchantDialog.setTitle(event.getTitle());
                addMerchantItemsToDialog(event, merchantDialog);
                merchantDialog.getElement().show(new EndNotify() {
                    @Override
                    public void perform() {
                        craftingDialog.moveToFront();
                    }
                });
            }
        });
    }

    private void addMerchantItemsToDialog(
            @Nonnull DialogMerchantReceivedEvent event, @Nonnull DialogMerchant dialog) {
        List<MerchantListEntry> sellingList = new ArrayList<>();
        List<MerchantListEntry> buyingList = new ArrayList<>();
        for (int i = 0; i < event.getItemCount(); i++) {
            NiftyMerchantItem item = new NiftyMerchantItem(nifty, event.getItem(i));

            switch (item.getType()) {
                case SellingItem:
                    sellingList.add(item);
                    break;
                case BuyingPrimaryItem:
                case BuyingSecondaryItem:
                    buyingList.add(item);
                    break;
            }
        }
        dialog.addAllSellingItems(sellingList);
        dialog.addAllBuyingItems(buyingList);
    }

    @EventSubscriber
    public void handleSelectDialogEvent(@Nonnull final DialogSelectionReceivedEvent event) {
        GlobalExecutorService.getService().submit(new Runnable() {
            @Override
            public void run() {
                showSelectDialog(event);
            }
        });
    }

    private void showSelectDialog(@Nonnull final DialogSelectionReceivedEvent event) {
        Element parentArea = screen.findElementById("windows");
        DialogSelectBuilder builder = new DialogSelectBuilder("selectDialog" + Integer.toString(event.getId()),
                                                                    event.getTitle());
        builder.dialogId(event.getId());
        builder.message(event.getMessage());

        int selectedWidth = 0;
        boolean useImages = false;
        Font textFont = FontLoader.getInstance().getFont(FontLoader.TEXT_FONT);
        for (int i = 0; i < event.getOptionCount(); i++) {
            SelectionItem item = event.getOption(i);
            useImages = useImages || (item.getId() > 0);
            selectedWidth = Math.max(selectedWidth, textFont.getWidth(item.getName()));
        }
        if (useImages) {
            selectedWidth += 79; // width of the image container area
        }
        selectedWidth += 2;  // padding of entry
        selectedWidth += 26; // padding of list box and window
        if (event.getOptionCount() > 6) {
            selectedWidth += 16; // space for the scroll bar
        }
        selectedWidth += 10; // padding to make it look good (some space on the right side of the text entries)
        selectedWidth += 20; // magical additional width of unknown origin (determined by testing)

        selectedWidth = Math.max(selectedWidth, 270); // width required to display the buttons properly

        builder.width(SizeValue.px(selectedWidth));
        builder.itemCount(Math.min(6, event.getOptionCount()));
        builders.add(new BuildWrapper(builder, parentArea, new PostBuildTask() {
            @Override
            public void run(@Nonnull Element createdElement) {
                DialogSelect dialog = createdElement.getNiftyControl(DialogSelect.class);
                if (dialog == null) {
                    LOGGER.warn("Newly created dialog was NULL");
                } else {
                    addSelectItemsToDialog(event, dialog);
                }
            }
        }));
    }

    private void addSelectItemsToDialog(
            @Nonnull DialogSelectionReceivedEvent event, @Nonnull DialogSelect dialog) {
        for (int i = 0; i < event.getOptionCount(); i++) {
            dialog.addItem(new NiftySelectItem(nifty, event.getOption(i)));
        }
    }

    @EventSubscriber
    public void handleTooltipRemovedEvent(TooltipsRemovedEvent event) {
        lastCraftingTooltip = -2;

        if (input.isAnyButtonDown(Button.Left, Button.Right)) {
            return;
        }
    }

    @NiftyEventSubscriber(id = "craftingDialog")
    public void handleCraftingCloseDialogEvent(String topic, @Nonnull DialogCraftingCloseEvent event) {
        closeCraftingDialog(event.getDialogId());
    }

    private void closeCraftingDialog(int id) {
        if (!openCraftDialog) {
            return;
        }
        World.getNet().sendCommand(new CloseDialogCraftingCmd(id));
        EventBus.publish(new CloseDialogEvent(id, CloseDialogEvent.DialogType.Crafting));
        openCraftDialog = false;
    }

    @NiftyEventSubscriber(id = "craftingDialog")
    public void handleCraftingCraftItemEvent(String topic, @Nonnull DialogCraftingCraftEvent event) {
        World.getNet()
                .sendCommand(new CraftItemCmd(event.getDialogId(), event.getItem().getItemIndex(), event.getCount()));
    }

    @NiftyEventSubscriber(id = "craftingDialog")
    public void handleCraftingIngredientLookAtEvent(
            String topic, @Nonnull DialogCraftingLookAtIngredientItemEvent event) {
        if (lastCraftingTooltip == event.getIngredientIndex()) {
            return;
        }

        if (input.isAnyButtonDown(Button.Left, Button.Right)) {
            return;
        }

        World.getNet().sendCommand(new LookAtCraftIngredientCmd(event.getDialogId(), event.getItem().getItemIndex(),
                                                                event.getIngredientIndex()));
        lastCraftingTooltip = event.getIngredientIndex();
    }

    @NiftyEventSubscriber(id = "craftingDialog")
    public void handleCraftingItemLookAtEvent(String topic, @Nonnull DialogCraftingLookAtItemEvent event) {
        if (lastCraftingTooltip == -1) {
            return;
        }

        if (input.isAnyButtonDown(Button.Left, Button.Right)) {
            return;
        }

        World.getNet().sendCommand(new LookAtCraftItemCmd(event.getDialogId(), event.getItem().getItemIndex()));
        lastCraftingTooltip = -1;
    }

    @Override
    public void bind(@Nonnull Nifty parentNifty, @Nonnull Screen parentScreen) {
        nifty = parentNifty;
        screen = parentScreen;

        merchantDialog = screen.findNiftyControl("merchantDialog", DialogMerchant.class);
        craftingDialog = screen.findNiftyControl("craftingDialog", DialogCrafting.class);
    }

    @Override
    public void onEndScreen() {
        AnnotationProcessor.unprocess(this);
        nifty.unsubscribeAnnotations(this);
    }

    @Override
    public void onStartScreen() {
        AnnotationProcessor.process(this);
        nifty.subscribeAnnotations(this);
    }

    @Override
    public void showCraftIngredientTooltip(
            int dialogId, int index, int ingredientIndex, @Nonnull Tooltip tooltip) {
        if ((craftingDialog == null) || (craftingDialog.getDialogId() != dialogId)) {
            return;
        }

        CraftingItemEntry selectedEntry = craftingDialog.getSelectedCraftingItem();
        if ((selectedEntry != null) && (selectedEntry.getItemIndex() == index)) {
            Element targetElement = craftingDialog.getIngredientItemDisplay(ingredientIndex);
            Rectangle elementRectangle = new Rectangle();
            elementRectangle.set(targetElement.getX(), targetElement.getY(), targetElement.getWidth(),
                                 targetElement.getHeight());
            tooltipHandler.showToolTip(elementRectangle, tooltip);
        }
    }

    @Override
    public void showCraftItemTooltip(int dialogId, int index, @Nonnull Tooltip tooltip) {
        if ((craftingDialog == null) || (craftingDialog.getDialogId() != dialogId)) {
            return;
        }

        CraftingItemEntry selectedEntry = craftingDialog.getSelectedCraftingItem();
        if ((selectedEntry != null) && (selectedEntry.getItemIndex() == index)) {
            Element targetElement = craftingDialog.getCraftingItemDisplay();
            Rectangle elementRectangle = new Rectangle();
            elementRectangle.set(targetElement.getX(), targetElement.getY(), targetElement.getWidth(),
                                 targetElement.getHeight());
            tooltipHandler.showToolTip(elementRectangle, tooltip);
        }
    }

    @Override
    public void showInputDialog(
            int id, String title, String description, int maxCharacters, boolean multipleLines) {
        Element parentArea = screen.findElementById("windows");
        DialogInputBuilder builder = new DialogInputBuilder("inputDialog" + Integer.toString(id), title);
        builder.description(description);
        builder.buttonLeft("OK");
        builder.buttonRight("Cancel");
        builder.dialogId(id);
        builder.maxLength(maxCharacters);
        if (multipleLines) {
            builder.style("illarion-dialog-input-multi");
        } else {
            builder.style("illarion-dialog-input-single");
        }
        builders.add(new BuildWrapper(builder, parentArea, null));
    }

    @Override
    public void showMessageDialog(int id, String title, String message) {
        Element parentArea = screen.findElementById("windows");
        DialogMessageBuilder builder = new DialogMessageBuilder("msgDialog" + Integer.toString(id), title);
        builder.text(message);
        builder.button("OK");
        builder.dialogId(id);
        builders.add(new BuildWrapper(builder, parentArea, null));
    }

    @Override
    public void update(@Nonnull GameContainer container, int delta) {
        while (true) {
            BuildWrapper wrapper = builders.poll();
            if (wrapper == null) {
                break;
            }

            Element element = wrapper.getBuilder().build(nifty, screen, wrapper.getParent());

            wrapper.executeTask(element);

            element.layoutElements();
            element.setConstraintX(SizeValue.px((wrapper.getParent().getWidth() - element.getWidth()) / 2));
            element.setConstraintY(SizeValue.px((wrapper.getParent().getHeight() - element.getHeight()) / 2));
            wrapper.getParent().layoutElements();
        }

        while (true) {
            CloseDialogEvent closeEvent = closers.poll();
            if (closeEvent == null) {
                break;
            }

            closeDialog(closeEvent);
        }
    }

    private void closeDialog(@Nonnull CloseDialogEvent event) {
        if (merchantDialog == null) {
            return;
        }
        Element parentArea = screen.findElementById("windows");
        if (parentArea == null) {
            return;
        }

        if (event.isClosingDialogType(CloseDialogEvent.DialogType.Merchant)) {
            if (event.getDialogId() == merchantDialog.getDialogId()) {
                merchantDialog.closeWindow();
                return;
            }
        }
        if (event.isClosingDialogType(CloseDialogEvent.DialogType.Crafting)) {
            if (event.getDialogId() == craftingDialog.getDialogId()) {
                craftingDialog.closeWindow();
                return;
            }
        }

        for (final Element child : parentArea.getChildren()) {
            String childId = child.getId();
            if (childId == null) {
                continue;
            }
            Matcher matcher = dialogNamePattern.matcher(childId);

            if (!matcher.find()) {
                continue;
            }

            try {
                String type = matcher.group(1);
                int id = Integer.parseInt(matcher.group(2));

                boolean wrongDialogType = false;
                switch (event.getDialogType()) {
                    case Any:
                        break;
                    case Message:
                        if (!"msg".equals(type)) {
                            wrongDialogType = true;
                        }
                        break;
                    case Input:
                        if (!"input".equals(type)) {
                            wrongDialogType = true;
                        }
                        break;
                    case Selection:
                        if (!"select".equals(type)) {
                            wrongDialogType = true;
                        }
                        break;
                    case Crafting:
                    case Merchant:
                        wrongDialogType = true;
                        break;
                }

                if (wrongDialogType) {
                    continue;
                }

                if ((event.getDialogId() == CloseDialogEvent.ALL_DIALOGS) || (event.getDialogId() == id)) {
                    child.hide(new EndNotify() {
                        @Override
                        public void perform() {
                            child.markForRemoval();
                        }
                    });
                }
            } catch (@Nonnull NumberFormatException ignored) {
                // nothing
            }
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    @NiftyEventSubscriber(id = "merchantDialog")
    public void handleMerchantBuyEvent(String topic, @Nonnull DialogMerchantBuyEvent event) {
        final MerchantList list = World.getPlayer().getMerchantList();

        if (list == null) {
            LOGGER.error("Buying event received, but there is not merchant list.");
            return;
        }

        final int index = event.getItem().getIndex();
        if (ItemCount.isGreaterOne(list.getItem(index).getBundleSize())) {
            list.buyItem(index);
        } else {
            if (input.isAnyKeyDown(Key.LeftShift, Key.RightShift)) {
                numberSelect.requestNewPopup(1, 250, new NumberSelectPopupHandler.Callback() {
                    @Override
                    public void popupCanceled() {
                        // nothing
                    }

                    @Override
                    public void popupConfirmed(int value) {
                        list.buyItem(index, ItemCount.getInstance(value));
                    }
                });
            } else {
                list.buyItem(index);
            }
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    @NiftyEventSubscriber(id = "merchantDialog")
    public void handleMerchantCloseEvent(String topic, DialogMerchantCloseEvent event) {
        MerchantList list = World.getPlayer().getMerchantList();
        if (list == null) {
            LOGGER.error("Close merchant list received, but there is not opened merchant list.");
        } else {
            list.closeDialog();
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    @NiftyEventSubscriber(pattern = "inputDialog[0-9]+")
    public void handleInputConfirmedEvent(String topic, @Nonnull DialogInputConfirmedEvent event) {
        if (event.getPressedButton() == DialogInput.DialogButton.LeftButton) {
            World.getNet().sendCommand(new CloseDialogInputCmd(event.getDialogId(), event.getText(), true));
        } else {
            World.getNet().sendCommand(new CloseDialogInputCmd(event.getDialogId(), "", false));
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    @NiftyEventSubscriber(pattern = "msgDialog[0-9]+")
    public void handleMessageConfirmedEvent(String topic, @Nonnull DialogMessageConfirmedEvent event) {
        World.getNet().sendCommand(new CloseDialogMessageCmd(event.getDialogId()));
    }

    @SuppressWarnings("MethodMayBeStatic")
    @NiftyEventSubscriber(pattern = "selectDialog[0-9]+")
    public void handleSelectionCancelEvent(String topic, @Nonnull DialogSelectCancelEvent event) {
        World.getNet().sendCommand(new CloseDialogSelectionCmd(event.getDialogId(), 0, false));
        EventBus.publish(new CloseDialogEvent(event.getDialogId(), CloseDialogEvent.DialogType.Selection));
    }

    @SuppressWarnings("MethodMayBeStatic")
    @NiftyEventSubscriber(pattern = "selectDialog[0-9]+")
    public void handleSelectionSelectEvent(String topic, @Nonnull DialogSelectSelectEvent event) {
        World.getNet().sendCommand(new CloseDialogSelectionCmd(event.getDialogId(), event.getItemIndex(), true));
        EventBus.publish(new CloseDialogEvent(event.getDialogId(), CloseDialogEvent.DialogType.Selection));
    }
}

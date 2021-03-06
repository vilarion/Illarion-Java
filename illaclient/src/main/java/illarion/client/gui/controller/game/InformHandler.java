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
import de.lessvoid.nifty.builder.EffectBuilder;
import de.lessvoid.nifty.builder.ElementBuilder;
import de.lessvoid.nifty.builder.PanelBuilder;
import de.lessvoid.nifty.controls.label.builder.LabelBuilder;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.tools.Color;
import de.lessvoid.nifty.tools.SizeValue;
import illarion.client.gui.InformGui;
import illarion.client.util.UpdateTask;
import illarion.client.world.World;
import org.illarion.engine.GameContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This handler is used to show and hide all the temporary inform messages on the screen. It provides the required
 * facilities to create and remove the elements.
 *
 * @author Martin Karing &gt;nitram@illarion.org&lt;
 */
public final class InformHandler implements InformGui, ScreenController {
    /**
     * This task is created as storage for the creation on the information display.
     */
    private final class InformBuildTask implements UpdateTask {
        /**
         * The element builder that is executed to create the inform message.
         */
        private final ElementBuilder builder;

        /**
         * The element that will be parent to the elements created by the builder.
         */
        private final Element parent;

        /**
         * The element that needs to get a new layout once the inform is displayed.
         */
        private final Element layoutParent;

        /**
         * Create a new instance of this build task that stores all the information needed to create the inform
         * message.
         *
         * @param informBuilder the element builder that creates the message
         * @param parentElement the parent element that will store the elements created by the element builder
         * @param layoutParent the element that needs to get a new layout once the inform is displayed
         */
        InformBuildTask(ElementBuilder informBuilder, Element parentElement, Element layoutParent) {
            builder = informBuilder;
            parent = parentElement;
            this.layoutParent = layoutParent;
        }

        @Override
        public void onUpdateGame(@Nonnull GameContainer container, int delta) {
            if (!parent.isVisible()) {
                parent.showWithoutEffects();
            }

            Element msg = builder.build(parentNifty, parentScreen, parent);
            msg.showWithoutEffects();
            layoutParent.layoutElements();
            msg.hide(new InformHandler.RemoveEndNotify(msg));
        }
    }

    /**
     * This utility class is a end notification that will trigger the removal of a target element. This is needed to
     * remove the server messages again from the screen.
     */
    private static final class RemoveEndNotify implements EndNotify {
        /**
         * The target element.
         */
        private final Element target;

        /**
         * The constructor of this class.
         *
         * @param element the element to remove
         */
        RemoveEndNotify(Element element) {
            target = element;
        }

        @Override
        public void perform() {
            target.markForRemoval(new InformHandler.LayoutElementsEndNotify(target.getParent()));
        }
    }

    /**
     * This utility class is a end notification that will trigger the calculation of the layout of a target element.
     * This is needed to put the parent container of the server informs back into shape.
     */
    private static final class LayoutElementsEndNotify implements EndNotify {
        /**
         * The target element.
         */
        private final Element target;

        /**
         * The constructor of this class.
         *
         * @param element the element to layout
         */
        LayoutElementsEndNotify(Element element) {
            target = element;
        }

        @Override
        public void perform() {
            target.layoutElements();

            if (target.getChildren().isEmpty()) {
                target.hide();
            }
        }
    }

    /**
     * The logger that is used for the logging output of this class.
     */
    private static final Logger log = LoggerFactory.getLogger(InformHandler.class);

    /**
     * The instance of the Nifty-GUI this handler is bound to.
     */
    private Nifty parentNifty;

    /**
     * The instance of the screen this handler is operating on.
     */
    private Screen parentScreen;

    /**
     * This is the panel that will be parent to all broadcast messages.
     */
    @Nullable
    private Element broadcastParentPanel;

    /**
     * This is the panel that will be parent to all server messages.
     */
    private Element serverParentPanel;

    /**
     * This is the panel that will be parent to all text to messages.
     */
    private Element textToParentPanel;

    /**
     * This is the panel that will be parent to all text-to messages.
     */
    private Element scriptParentPanel;

    @Override
    public void bind(@Nonnull Nifty nifty, @Nonnull Screen screen) {
        parentNifty = nifty;
        parentScreen = screen;

        broadcastParentPanel = screen.findElementById("broadcastMsgPanel");
        serverParentPanel = screen.findElementById("serverMsgPanel");
        textToParentPanel = screen.findElementById("textToMsgPanel");
        scriptParentPanel = screen.findElementById("scriptMessagePanel");
    }

    @Override
    public void onEndScreen() {
        // nothing to do
    }

    @Override
    public void onStartScreen() {
        // nothing to do
    }

    @Override
    public void showBroadcastInform(@Nonnull String message) {
        if (broadcastParentPanel == null) {
            log.warn("Received server inform before the GUI became ready.");
            return;
        }

        LabelBuilder labelBuilder = new LabelBuilder();
        labelBuilder.label(message);
        labelBuilder.font("menuFont");
        labelBuilder.invisibleToMouse();

        EffectBuilder effectBuilder = new EffectBuilder("hide");
        effectBuilder.startDelay(10000 + (message.length() * 50));
        labelBuilder.onHideEffect(effectBuilder);

        showInform(labelBuilder, broadcastParentPanel, broadcastParentPanel.getParent());
    }

    /**
     * Show a inform on the screen.
     *
     * @param informBuilder the builder that is meant to create the inform message
     * @param parent the parent element that stores the inform message
     * @param layoutParent the element that needs to get its layout recalculated
     */
    public void showInform(ElementBuilder informBuilder, Element parent, Element layoutParent) {
        World.getUpdateTaskManager().addTask(new InformBuildTask(informBuilder, parent, layoutParent));
    }

    /**
     * Show a script inform message on the screen.
     *
     * @param priority the priority of the message
     * @param message the message
     */
    @Override
    public void showScriptInform(int priority, @Nonnull String message) {
        if (scriptParentPanel == null) {
            log.warn("Received script inform before the GUI became ready. Priority: {} Message: {}", priority, message);
            return;
        }

        PanelBuilder panelBuilder = new PanelBuilder();
        panelBuilder.childLayoutHorizontal();
        panelBuilder.width(SizeValue.percent(75));
        panelBuilder.alignCenter();

        LabelBuilder labelBuilder = new LabelBuilder();
        panelBuilder.control(labelBuilder);
        labelBuilder.label(message);
        labelBuilder.font("textFont");
        switch (priority) {
            case 1:
                labelBuilder.color(Color.WHITE);
                break;
            case 2:
                labelBuilder.color(new Color(1.0f, 0.5f, 0.5f, 1.0f));
                break;
            default:
                labelBuilder.color(new Color(0.9f, 0.9f, 0.9f, 1.0f));
        }
        labelBuilder.invisibleToMouse();
        labelBuilder.valignCenter();
        labelBuilder.alignCenter();
        labelBuilder.width(SizeValue.percent(100));
        labelBuilder.textHAlignCenter();
        labelBuilder.parameter("wrap", "true");

        EffectBuilder moveEffectBuilder = new EffectBuilder("move");
        moveEffectBuilder.length(getScriptInformDisplayTime(message, priority));
        moveEffectBuilder.startDelay(0);
        moveEffectBuilder.effectParameter("mode", "toOffset");
        moveEffectBuilder.effectParameter("direction", "bottom");
        moveEffectBuilder.effectParameter("offsetY", "-80");
        labelBuilder.onHideEffect(moveEffectBuilder);

        EffectBuilder fadeOutBuilder = new EffectBuilder("fade");
        fadeOutBuilder.length(getScriptInformDisplayTime(message, priority));
        fadeOutBuilder.startDelay(0);
        fadeOutBuilder.effectParameter("start", "FF");
        fadeOutBuilder.effectParameter("end", "00");
        labelBuilder.onHideEffect(fadeOutBuilder);

        panelBuilder.onHideEffect(new EffectBuilder("hide"));

        showInform(panelBuilder, scriptParentPanel, scriptParentPanel.getParent());
    }

    private static int getScriptInformDisplayTime(@Nonnull CharSequence text, int priority) {
        if (priority == 0) {
            return 5000 + (text.length() * 50);
        }
        return 8000 + (text.length() * 50);
    }

    @Override
    public void showServerInform(@Nonnull String message) {
        if (serverParentPanel == null) {
            log.warn("Received server inform before the GUI became ready.");
            return;
        }

        PanelBuilder panelBuilder = new PanelBuilder();
        panelBuilder.childLayoutHorizontal();

        LabelBuilder labelBuilder = new LabelBuilder();
        panelBuilder.control(labelBuilder);
        labelBuilder.label("Server> " + message);
        labelBuilder.font("consoleFont");
        labelBuilder.invisibleToMouse();

        EffectBuilder effectBuilder = new EffectBuilder("hide");
        effectBuilder.startDelay(10000 + (message.length() * 50));
        panelBuilder.onHideEffect(effectBuilder);

        showInform(panelBuilder, serverParentPanel, serverParentPanel.getParent());
    }

    @Override
    public void showTextToInform(@Nonnull String message) {
        if (textToParentPanel == null) {
            log.warn("Received server inform before the GUI became ready.");
            return;
        }

        LabelBuilder labelBuilder = new LabelBuilder();
        labelBuilder.label(message);
        labelBuilder.font("textFont");
        labelBuilder.invisibleToMouse();

        EffectBuilder effectBuilder = new EffectBuilder("hide");
        effectBuilder.startDelay(10000 + (message.length() * 50));
        labelBuilder.onHideEffect(effectBuilder);

        showInform(labelBuilder, textToParentPanel, textToParentPanel.getParent());
    }
}

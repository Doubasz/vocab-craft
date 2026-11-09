package com.ferhat.vocabcraft.client.hud;

import com.ferhat.vocabcraft.net.ChallengeResultPayload;
import com.ferhat.vocabcraft.net.ChallengeStartPayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Quiz panel in the top-left corner. Does not take input or pause the game; the player answers in chat.
 *
 * <pre>
 * ┃ SAY IT IN JAPANESE                 12s
 * ┃ water                                     (2x scale)
 * ┃ ██████████████░░░░░░░░░░░░░░░░░░░░░░
 * ┃ > mizu_
 * </pre>
 */
public final class QuizHud implements HudElement {
	private static final int MARGIN = 8;
	private static final int PADDING = 8;
	private static final int ACCENT_WIDTH = 3;
	private static final int MIN_CONTENT_WIDTH = 180;
	private static final float PROMPT_SCALE = 2.0F;
	private static final int BAR_HEIGHT = 3;

	private static final int BACKGROUND = 0xD0101014;
	private static final int YELLOW = 0xFFF2C94C;
	private static final int GREEN = 0xFF6FCF97;
	private static final int RED = 0xFFEB5757;
	private static final int JAPANESE_PROMPT = 0xFFFF8080;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFB0B0B0;
	private static final int BAR_BACKGROUND = 0xFF3A3A3A;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ChallengeStartPayload challenge = ClientChallengeState.challenge();
		if (challenge == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		ChallengeResultPayload result = ClientChallengeState.result();

		int accent = result == null ? YELLOW : result.correct() ? GREEN : RED;
		String header = challenge.toJapanese() ? "SAY IT IN JAPANESE" : "SAY IT IN ENGLISH";
		int remaining = ClientChallengeState.remainingTicks();
		String seconds = result == null ? (remaining + 19) / 20 + "s" : "";
		String prompt = challenge.prompt();
		BottomLine bottom = bottomLine(minecraft, result);

		int promptWidth = Math.round(font.width(prompt) * PROMPT_SCALE);
		int maxContentWidth = graphics.guiWidth() - 2 * MARGIN - 2 * PADDING - ACCENT_WIDTH;
		int contentWidth = Math.min(maxContentWidth, Math.max(MIN_CONTENT_WIDTH,
				Math.max(promptWidth, font.width(header) + 16 + font.width(seconds))));
		int promptHeight = Math.round(font.lineHeight * PROMPT_SCALE);
		int height = PADDING + font.lineHeight + 4 + promptHeight + 6 + BAR_HEIGHT + 6 + font.lineHeight + PADDING;

		int left = MARGIN;
		int top = MARGIN;
		int x = left + ACCENT_WIDTH + PADDING;
		int right = x + contentWidth;

		graphics.fill(left, top, right + PADDING, top + height, BACKGROUND);
		graphics.fill(left, top, left + ACCENT_WIDTH, top + height, accent);

		// Header + seconds left
		int y = top + PADDING;
		graphics.text(font, header, x, y, GRAY, false);
		graphics.text(font, seconds, right - font.width(seconds), y, WHITE, false);

		// Prompt, drawn at 2x
		y += font.lineHeight + 4;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(PROMPT_SCALE, PROMPT_SCALE);
		String fittedPrompt = font.plainSubstrByWidth(prompt, Math.round(contentWidth / PROMPT_SCALE));
		graphics.text(font, fittedPrompt, 0, 0, challenge.toJapanese() ? WHITE : JAPANESE_PROMPT, true);
		graphics.pose().popMatrix();

		// Time bar
		y += promptHeight + 6;
		float partial = result == null ? deltaTracker.getGameTimeDeltaPartialTick(false) : 0;
		float fraction = Math.clamp((remaining - partial) / Math.max(1, challenge.timeLimitTicks()), 0F, 1F);
		graphics.fill(x, y, right, y + BAR_HEIGHT, BAR_BACKGROUND);
		graphics.fill(x, y, x + Math.round(contentWidth * fraction), y + BAR_HEIGHT, accent);

		// Typed answer or result
		y += BAR_HEIGHT + 6;
		graphics.text(font, font.plainSubstrByWidth(bottom.text(), contentWidth), x, y, bottom.color(), false);
	}

	private static BottomLine bottomLine(Minecraft minecraft, ChallengeResultPayload result) {
		if (result != null) {
			if (result.correct()) {
				return new BottomLine("✓ " + result.solution(), GREEN);
			}
			String said = result.answer().isEmpty() ? "time's up" : "\"" + result.answer() + "\"";
			return new BottomLine("✗ " + said + "  →  " + result.solution(), RED);
		}
		String typed = typedChatText(minecraft.gui.screen());
		if (typed != null) {
			boolean cursorVisible = (System.currentTimeMillis() / 500) % 2 == 0;
			return new BottomLine("> " + typed + (cursorVisible ? "_" : ""), WHITE);
		}
		return new BottomLine("press " + minecraft.options.keyChat.getTranslatedKeyMessage().getString()
				+ " to answer", GRAY);
	}

	/** Text currently typed in the chat box, or null if chat is closed or a command is being typed. */
	private static String typedChatText(Screen screen) {
		if (!(screen instanceof ChatScreen chat)) {
			return null;
		}
		for (GuiEventListener child : chat.children()) {
			if (child instanceof EditBox box) {
				String value = box.getValue();
				return value.startsWith("/") ? null : value;
			}
		}
		return null;
	}

	private record BottomLine(String text, int color) {
	}
}

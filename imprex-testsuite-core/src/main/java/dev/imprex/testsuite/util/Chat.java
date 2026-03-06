package dev.imprex.testsuite.util;

import java.util.function.Consumer;

import com.mojang.brigadier.context.CommandContext;

import dev.imprex.testsuite.api.TestsuiteSender;
import dev.imprex.testsuite.api.TestsuiteServer;
import dev.imprex.testsuite.server.ServerInstance;
import dev.imprex.testsuite.util.ChatMessageBuilder.ChatMessageSenderBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class Chat {

	public static final Component PREFIX = Component.text("")
			.append(Component.text("[").color(TextColor.color(60, 70, 200)))
			.append(Component.text("TSuite").color(TextColor.color(60, 180, 200)))
			.append(Component.text("]").color(TextColor.color(60, 70, 200)))
			.append(Component.space());

	public static ChatMessageBuilder<?> builder() {
		return new ChatMessageBuilder<>();
	}

	public static ChatMessageBuilder<?> builder(boolean prefix) {
		return new ChatMessageBuilder<>(prefix);
	}

	public static ChatMessageBuilder<?> builder(TestsuiteServer instance) {
		return new ChatMessageBuilder<>(instance);
	}

	public static ChatMessageBuilder<?> builder(boolean prefix, TestsuiteServer instance) {
		return new ChatMessageBuilder<>(prefix, instance);
	}

	public static int send(CommandContext<TestsuiteSender> sender, Consumer<ChatMessageBuilder<?>> builder) {
		return Chat.send(sender.getSource(), builder);
	}

	public static int send(TestsuiteSender sender, Consumer<ChatMessageBuilder<?>> builder) {
		ChatMessageSenderBuilder chatMessageBuilder = new ChatMessageSenderBuilder(sender);
		builder.accept(chatMessageBuilder);
		return chatMessageBuilder.send();
	}

	public static String format(String message, Object... args) {
		char[] array = message.toCharArray();

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < array.length; i++) {
			char letter = array[i];

			if (array[i] == '{' && array.length > i + 2 && array[i + 2] == '}') {	
				try {
					int index = Integer.valueOf(String.valueOf(array[i + 1]));

					i += 2;

					if (args.length > index) {
						builder.append(args[index].toString());
					}
				} catch (NumberFormatException e) {
				}
				continue;
			}

			builder.append(letter);
		}

		return builder.toString();
	}

	public static class Color {

		public static final TextColor DARK_GRAY = TextColor.color(100, 100, 120);
		public static final TextColor GRAY = TextColor.color(180, 180, 200);
		public static final TextColor RED = TextColor.color(200, 40, 40);
		public static final TextColor ORANGE = TextColor.color(255, 170, 0);
		public static final TextColor DARK_GREEN = TextColor.color(60, 180, 60);
		public static final TextColor LIGHT_GREEN = TextColor.color(140, 200, 130);
		public static final TextColor PURPLE = TextColor.color(200, 40, 200);

		public static TextColor statusColor(ServerInstance server) {
			return switch (server.getServerStatus()) {
				case INSTALLING -> PURPLE;
				default -> switch (server.getStatus()) {
						case STARTING -> LIGHT_GREEN;
						case RUNNING -> DARK_GREEN;
						case STOPPING -> ORANGE;
						default -> RED;
					};
				};
		}
	}
}
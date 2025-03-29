package dev.imprex.testsuite.command.command;

import static dev.imprex.testsuite.command.ArgumentBuilder.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.api.TestsuitePlayer;
import dev.imprex.testsuite.api.TestsuiteSender;
import dev.imprex.testsuite.api.TestsuiteServer;
import dev.imprex.testsuite.util.Chat;

public class CommandTyl {

	public CommandTyl(TestsuitePlugin plugin) {
	}

	public LiteralArgumentBuilder<TestsuiteSender> create() {
		return literal("tyl")
				.executes(this::printCurrentServer);
	}

	public int printCurrentServer(CommandContext<TestsuiteSender> context) {
		if (context.getSource() instanceof TestsuitePlayer player) {
			TestsuiteServer serverConnection = player.getServer();
			if (serverConnection == null) {
				Chat.builder().append("Your currently not connected to any server!").send(context);
				return Command.SINGLE_SUCCESS;
			}

			String serverName = serverConnection.getName();
			Chat.builder().append("Your currently connected to {0}", serverName).send(context);
		} else {
			Chat.builder().append("Server was not found!").send(context);
		}
		return Command.SINGLE_SUCCESS;
	}
}
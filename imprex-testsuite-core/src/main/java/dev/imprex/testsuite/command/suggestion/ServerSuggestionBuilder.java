package dev.imprex.testsuite.command.suggestion;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.mattmalec.pterodactyl4j.UtilizationState;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.imprex.testsuite.api.TestsuiteSender;
import dev.imprex.testsuite.command.ArgumentBuilder;
import dev.imprex.testsuite.server.ServerInstance;

public class ServerSuggestionBuilder extends SuggestionBuilder<ServerInstance, ServerInstance> {

	public ServerSuggestionBuilder(Supplier<Stream<ServerInstance>> supplier) {
		super(supplier);
	}

	public ServerSuggestionBuilder hasStatus(UtilizationState first, UtilizationState... status) {
		EnumSet<UtilizationState> statusSet = EnumSet.of(first, status);
		return (ServerSuggestionBuilder) this.filter(server -> statusSet.contains(server.getStatus()));
	}

	@Override
	public SuggestionProvider<TestsuiteSender> buildSuggest() {
		final Function<Stream<ServerInstance>, Stream<String>> transformation = this.map(ServerInstance::getName)
				.buildStream();

		return (context, builder) -> {
			transformation.apply(this.supplier.get())
				.map(Objects::toString)
				.forEach(builder::suggest);
			return builder.buildFuture();
		};
	}

	@Override
	public SuggestionProvider<TestsuiteSender> buildSuggest(String fieldName) {
		final Function<Stream<ServerInstance>, Stream<String>> transformation = this.map(ServerInstance::getName)
				.buildStream();

		return (context, builder) -> {
			String input = ArgumentBuilder.getSafeStringArgument(context, fieldName, "");
			String[] keywords = input.toLowerCase().split("[-_. ]");

			transformation.apply(this.supplier.get())
				.map(Objects::toString)
				.filter(name -> {
					for (String keyword : keywords) {
						if (!name.toLowerCase().contains(keyword)) {
							return false;
						}
					}
					return true;
				})
				.forEach(builder::suggest);
			return builder.buildFuture();
		};
	}
}
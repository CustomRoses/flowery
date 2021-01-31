package Flowery;

import discord4j.core.event.domain.message.MessageCreateEvent;
import reactor.core.publisher.Mono;

public class Interfaces {
	interface Command {
		 Mono<Void> execute(MessageCreateEvent event);
	}
	
	
}

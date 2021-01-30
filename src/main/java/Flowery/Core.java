package Flowery;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.voice.AudioProvider;
import discord4j.voice.VoiceConnection;
import Flowery.Interfaces.Command;

public class Core {

	public static void main(String[] args) {

		// Creates AudioPlayer instances and translates URLs to AudioTrack instances
		final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

		// Uses magic to make the bot run faster idk
		playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);

		// Allow playerManager to parse remote sources like YouTube links
		AudioSourceManagers.registerRemoteSources(playerManager);

		// Create an AudioPlayer so Discord4J can receive audio data
		final AudioPlayer player = playerManager.createPlayer();

		// Create a LavaPlayerAudioProvider
		AudioProvider provider = new LavaPlayerAudioProvider(player);

		// create a Client and log in
		final GatewayDiscordClient client = DiscordClientBuilder.create(args[0]).build().login().block();

		// Log successful login to console
		client.getEventDispatcher().on(ReadyEvent.class).subscribe(event -> {
			final User self = event.getSelf();
			System.out.println(String.format("Logged in as %s#%s", self.getUsername(), self.getDiscriminator()));
		});

		// process commands that are sent in chat
		client.getEventDispatcher().on(MessageCreateEvent.class)
				// subscribe is like block, in that it will *request* for action
				// to be done, but instead of blocking the thread, waiting for it
				// to finish, it will just execute the results asynchronously.
				.subscribe(event -> {

					final String content = event.getMessage().getContent();
					if (!event.getMessage().getAuthorAsMember().block().isBot()) {
						for (final Map.Entry<String, Command> entry : commands.entrySet()) {
							// We will be using as our "prefix" to any command in the system.
							if (content.startsWith('¬' + entry.getKey())) {
								entry.getValue().execute(event);
								break;
							}
						}
					}
				});
		commands.put("kick", event -> {
			final discord4j.core.object.entity.Member member = event.getMember().orElse(null);
			final VoiceState voiceState = member.getVoiceState().block();
			Set<Snowflake> kickees = event.getMessage().getUserMentionIds();
			kickees.forEach(user -> {
				voiceState.getChannel().block().getVoiceStates().subscribe(target -> {
					if (user == target.getUserId()) {
						target.getMember().block().kick().block();
						System.out.println("Kicked Someone");
					}

				});
			});

		});

		// Simple Ping Command to check if the bot is live
		commands.put("ping", event -> {
			event.getMessage().getChannel().block().createMessage("<:pinged:747783377322508290>").block();
		});

		commands.put("info", event -> {
			MessageChannel channel = event.getMessage().getChannel().block();
			channel.createEmbed(spec -> {
				spec.addField("Author", "CustomRoses", true).addField("Version", "0.0.1", true)
						.addField("Framework", "Discord4J", true).setTitle("info").setAuthor("Flowery",
								"http://pixelartmaker.com/art/8a60641ddf930e4.png",
								"http://pixelartmaker.com/art/8a60641ddf930e4.png");
			}).block();
		});

		commands.put("help", event -> {
			MessageChannel channel = event.getMessage().getChannel().block();
			channel.createEmbed(spec -> {
				spec.addField("ping", "tests if the bot is online", false)
						.addField("join", "joins your current voice channel. Prerequisite for play and leave", false)
						.addField("leave", "Disconnects from the current voice channel", false)
						.addField("play", "Adds a provided youtube URL to the queue", false)
						.addField("mhm", "mhm.", false)
						.setAuthor("Flowery", "http://pixelartmaker.com/art/8a60641ddf930e4.png",
								"http://pixelartmaker.com/art/8a60641ddf930e4.png")
						.setTitle("Use prefix \"¬\"");
			}).block();
		});

		commands.put("mhm", event -> {
			event.getMessage().getChannel().block().createMessage("mhm.").block();
			event.getMessage().delete().block();
		});

		// Music Join Command
		commands.put("join", event -> {
			final discord4j.core.object.entity.Member member = event.getMember().orElse(null);
			if (member != null) {
				final VoiceState voiceState = member.getVoiceState().block();
				if (voiceState != null) {
					final VoiceChannel channel = voiceState.getChannel().block();
					if (channel != null) {
						VoiceConnection connection = channel.join(spec -> spec.setProvider(provider)).block();
						event.getMessage().getChannel().block().createMessage("Joined " + member.getDisplayName()
								+ " in " + member.getVoiceState().block().getChannel().block().getName()).block();
						System.out.println("Joined a channel!");
						client.getEventDispatcher().on(MessageCreateEvent.class).subscribe(thing -> {
							if (thing.getMessage().getContent().equals("¬leave")) {
								thing.getMessage().getChannel().block()
										.createMessage("Disconnecting from "
												+ member.getVoiceState().block().getChannel().block().getName())
										.block();
								connection.disconnect().block();
								client.getEventDispatcher().shutdown();
								main(args);

							}
							if (thing.getMessage().getContent().equals("¬shutdown")) {
								connection.disconnect();
								event.getClient().logout().block();
								System.out.println("shutting down");
							}

						});
					}

				}
			}
		});

		// Play Music Command
		final TrackScheduler scheduler = new TrackScheduler(player);
		commands.put("play", event -> {

			final String content = event.getMessage().getContent();
			final List<String> command = Arrays.asList(content.split(" "));
			if (command.get(1).contains("youtu.be") || command.get(1).contains("youtube")) {
				playerManager.loadItem(command.get(1), scheduler);
				event.getMessage().getChannel().block().createMessage("Added to the queue").block();
			} else {
				event.getMessage().getChannel().block().createMessage(command.get(1) + " is not a valid URL").block();
			}

		});

		client.onDisconnect().block();

	}

	private static final Map<String, Command> commands = new HashMap<>();

	static {

	}
}
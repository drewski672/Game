/**
* Generated By NPCScript :: A scripting engine created for openrsc by Zilent
*/

package org.openrsc.server.npchandler.Dragon_Slayer;

import org.openrsc.server.Config;
import org.openrsc.server.event.DelayedGenericMessage;
import org.openrsc.server.event.DelayedQuestChat;
import org.openrsc.server.event.SingleEvent;
import org.openrsc.server.model.ChatMessage;
import org.openrsc.server.model.MenuHandler;
import org.openrsc.server.model.Npc;
import org.openrsc.server.model.Player;
import org.openrsc.server.model.Quest;
import org.openrsc.server.model.Quests;
import org.openrsc.server.model.World;
import org.openrsc.server.npchandler.NpcHandler;
public class Ned_On_Ship implements NpcHandler {
	public void handleNpc(final Npc npc, final Player owner) throws Exception {
		npc.blockedBy(owner);
		owner.setBusy(true);
		Quest q = owner.getQuest(Quests.DRAGON_SLAYER);
		if(q != null) {
			if(q.finished()) {
				howDidYou(npc, owner);
			} else if(owner.onCrandor()) {	//@ CRANDOR
				final String[] options0 = {"Is the ship ready to sail back?", "So are you enjoying this exotic island vacation?"};
				owner.setBusy(false);
				owner.sendMenu(options0);
				owner.setMenuHandler(new MenuHandler(options0) {
					public void handleReply(final int option, final String reply) {
						owner.setBusy(true);
						for(Player informee : owner.getViewArea().getPlayersInView()) {
							informee.informOfChatMessage(new ChatMessage(owner, reply, npc));
						}
						switch(option) {
							case 0:
								goBack(npc, owner);
								break;
							case 1:
								vacation(npc, owner);
								break;
						}
					}
				});
			} else {					//@ SARIM
				final String[] messages0 = {"Hello there lad"};
				World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, messages0, true) {
					public void finished() {
						World.getDelayedEventHandler().add(new SingleEvent(owner, 1500) {
							public void action() {
								final String[] options0 = {"So are you going to take me to Crandor Island now then?", "So are you still up to sailing this ship?"};
								owner.setBusy(false);
								owner.sendMenu(options0);
								owner.setMenuHandler(new MenuHandler(options0) {
									public void handleReply(final int option, final String reply) {
										owner.setBusy(true);
										for(Player informee : owner.getViewArea().getPlayersInView()) {
											informee.informOfChatMessage(new ChatMessage(owner, reply, npc));
										}
										switch(option) {
											case 0:
												goNow(npc, owner);
												break;
											case 1:
												sailShip(npc, owner);
												break;
										}
									}
								});
							}
						});
					}
				});
			}
		} else {
			owner.sendMessage("@red@Error with Dragon Slayer");
			owner.sendMessage("@red@You weren't supposed to talk with him yet!");
			owner.setBusy(false);
			npc.unblock();
		}
	}

	private void howDidYou(final Npc npc, final Player owner) {
		World.getDelayedEventHandler().add(new DelayedQuestChat(owner, npc, new String[] {"Ned! How did you get back from Crandor?"}, true) {
			public void finished() {
				World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, new String[] {"Oh, a passing whale decided to give me a tow"}) {
					public void finished() {
						owner.setBusy(false);
						npc.unblock();
					}
				});
			}
		});
	}
	
	private void goBack(final Npc npc, final Player owner) {
		if(owner.ladyFixed()) {
			World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, new String[] {"Aye, the ship is seaworthy again"}) {
				public void finished() {
					World.getDelayedEventHandler().add(new DelayedGenericMessage(owner, new String[] {"You feel the ship begin to move", "You are out at sea", "The ship is sailing", "The ship is sailing", "You arrive at Port Sarim"}, 2500) {
						public void finished() {
							World.getDelayedEventHandler().add(new SingleEvent(owner, 1000) {
								public void action() {
									World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, new String[] {"Aha we've arrived"}) {
										public void finished() {
											owner.setCrandor(false);
											owner.setBusy(false);
											npc.unblock();
										}
									});
								}
							});

						}
					});
				}
			});
		} else {
			World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, new String[] {"Well when we arrived the ship took a nasty jar from those rocks", "We may be stranded"}) {
				public void finished() {
					owner.setBusy(false);
					npc.unblock();
				}
			});
		}

	}
	
	private void vacation(final Npc npc, final Player owner) {
		final String[] messages1 = {"Well it would have been better if I'd brought my sun lotion", "Oh and the skeletons which won't let me leave the ship", "Probably aren't helping either"};
		World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, messages1) {
			public void finished() {
				owner.setBusy(false);
				npc.unblock();
			}
		});
	}
	
	private void sailShip(final Npc npc, final Player owner) {
		final String[] messages1 = {"Well I am a tad rusty", "I'm sure it'll all come back to me, once I get into action", "I hope..."};
		World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, messages1) {
			public void finished() {
				owner.setBusy(false);
				npc.unblock();
			}
		});
	}

	private void goNow(final Npc npc, final Player owner) {
		if(owner.ladyFixed()) {
			World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, new String[] {"Ok show me the map and we'll set sail now"}) {
				public void finished() {
					if(owner.getInventory().countId(415) > 0) {
						World.getDelayedEventHandler().add(new DelayedQuestChat(owner, npc, new String[] {"Here it is"}) {
							public void finished() {
								owner.sendMessage("You show the map to Ned");
								World.getDelayedEventHandler().add(new DelayedGenericMessage(owner, new String[] {"You feel the ship begin to move", "You are out at sea", "The ship is sailing", "The ship is sailing", "You feel a crunch"}, 2500) {
									public void finished() {
										World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, new String[] {"Aha we've arrived"}) {
											public void finished() {
												owner.setCrandor(true);
												owner.teleport(280, 3473, false);
												owner.breakShip();
												owner.setBusy(false);
												npc.unblock();
											}
										});
									}
								});
							}
						});
					} else {
						World.getDelayedEventHandler().add(new DelayedQuestChat(owner, npc, new String[] {"I don't have the map yet"}) {
							public void finished() {
								World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, new String[] {"Ah ok, come back when you do"}) {
									public void finished() {
										owner.setBusy(false);
										npc.unblock();
									}
								});
							}
						});
					}
				}
			});			
		} else {
			World.getDelayedEventHandler().add(new DelayedQuestChat(npc, owner, new String[] {"The ship isn't in a seaworthy state yet"}) {
				public void finished() {
					owner.setBusy(false);
					npc.unblock();
				}
			});
		}

	}
}
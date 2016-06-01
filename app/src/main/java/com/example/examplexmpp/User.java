package com.example.examplexmpp;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.RosterEntry;

public class User {
	public RosterEntry entry;
	public Presence presence;
	private String toS;

	@Override
	public String toString() {
		if (toS == null) {
			StringBuilder sb = new StringBuilder();
			sb.append(entry.getName()).append("(").append(entry.getUser())
					.append(")\n");
			if (presence.getType() == Presence.Type.available) {
				sb.append("(online)");
			} else {
				sb.append("(offline)");
			}
			sb.append(presence.getStatus());
			toS = sb.toString();
		}
		return toS;
	}
}

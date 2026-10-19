package splitter.domain;

import java.util.ArrayList;
import java.util.List;

public final class Members {
    private final List<Member> members;

    public Members() {
        this.members = new ArrayList<>();
    }

    public Members(List<Member> existing) {
        this.members = new ArrayList<>(existing);
    }

    public List<Member> all() {
        return List.copyOf(members);
    }

    public List<String> order() {
        return members.stream().map(Member::name).toList();
    }

    public List<String> activeNames() {
        return members.stream().filter(Member::active).map(Member::name).toList();
    }

    public Member add(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new SplitterException("Member name must not be empty");
        }
        if (trimmed.contains(",") || trimmed.contains("=")) {
            throw new SplitterException("Member name '" + trimmed + "' must not contain ',' or '='");
        }
        if (trimmed.equalsIgnoreCase("everyone")) {
            throw new SplitterException("'everyone' is a reserved name");
        }
        int index = indexOf(trimmed);
        if (index < 0) {
            Member created = new Member(trimmed, true);
            members.add(created);
            return created;
        }
        Member existing = members.get(index);
        if (existing.active()) {
            throw new SplitterException(existing.name() + " is already in the flat");
        }
        Member reactivated = new Member(existing.name(), true);
        members.set(index, reactivated);
        return reactivated;
    }

    public String resolve(String input) {
        int index = indexOf(input.trim());
        if (index < 0) {
            throw new SplitterException("Unknown member '" + input + "'");
        }
        return members.get(index).name();
    }

    public String resolveActive(String input) {
        String name = resolve(input);
        if (!members.get(indexOf(name)).active()) {
            throw new SplitterException(name + " has been removed from the flat");
        }
        return name;
    }

    public void deactivate(String canonicalName) {
        int index = indexOf(canonicalName);
        if (index < 0) {
            throw new SplitterException("Unknown member '" + canonicalName + "'");
        }
        members.set(index, new Member(members.get(index).name(), false));
    }

    private int indexOf(String name) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}

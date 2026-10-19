package splitter.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class Ledger {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final Members members = new Members();
    private final List<Expense> expenses = new ArrayList<>();
    private int nextExpenseId = 1;

    public Members members() {
        return members;
    }

    public List<Expense> expenses() {
        return Collections.unmodifiableList(expenses);
    }

    public Expense expense(int id) {
        return expenses.get(indexOfExpense(id));
    }

    public Member addMember(String name) {
        return members.add(name);
    }

    public Expense addExpense(String description, BigDecimal amount, String paidBy, SplitSpec spec, LocalDate date) {
        String cleanDescription = validDescription(description);
        BigDecimal cleanAmount = validAmount(amount);
        String payer = members.resolveActive(paidBy);
        Split split = resolveSplit(spec, members::resolveActive);
        Expense expense = new Expense(nextExpenseId, cleanDescription, cleanAmount, payer, split, date);
        expenses.add(expense);
        nextExpenseId++;
        return expense;
    }

    public Expense editExpense(int id, ExpenseEdit edit) {
        int index = indexOfExpense(id);
        Expense old = expenses.get(index);
        Set<String> existingNames = new HashSet<>(old.split().people());
        existingNames.add(old.paidBy());
        // Names already on the expense stay editable even if that member was since removed.
        Function<String, String> resolver = input -> {
            String name = members.resolve(input);
            return existingNames.contains(name) ? name : members.resolveActive(input);
        };

        String description = edit.description().map(Ledger::validDescription).orElse(old.description());
        BigDecimal amount = edit.amount().map(Ledger::validAmount).orElse(old.amount());
        String payer = edit.paidBy().map(resolver).orElse(old.paidBy());
        Split split = edit.split().map(spec -> resolveSplit(spec, resolver)).orElse(old.split());

        Expense updated = new Expense(old.id(), description, amount, payer, split, old.date());
        expenses.set(index, updated);
        return updated;
    }

    public Expense deleteExpense(int id) {
        return expenses.remove(indexOfExpense(id));
    }

    private int indexOfExpense(int id) {
        for (int i = 0; i < expenses.size(); i++) {
            if (expenses.get(i).id() == id) {
                return i;
            }
        }
        throw new SplitterException("No expense #" + id);
    }

    private static String validDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new SplitterException("Description must not be blank");
        }
        return description.trim();
    }

    private static BigDecimal validAmount(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new SplitterException("Amount must be greater than zero");
        }
        return amount.setScale(2);
    }

    private Split resolveSplit(SplitSpec spec, Function<String, String> resolver) {
        return switch (spec) {
            case SplitSpec.Everyone e -> {
                List<String> active = members.activeNames();
                if (active.isEmpty()) {
                    throw new SplitterException("There are no members to split between");
                }
                yield new Split.Equal(List.copyOf(active));
            }
            case SplitSpec.Equal e -> {
                if (e.names().isEmpty()) {
                    throw new SplitterException("Split needs at least one person");
                }
                List<String> resolved = new ArrayList<>();
                for (String name : e.names()) {
                    resolved.add(resolver.apply(name));
                }
                rejectDuplicates(resolved);
                yield new Split.Equal(List.copyOf(resolved));
            }
            case SplitSpec.Percent p -> {
                if (p.percentages().isEmpty()) {
                    throw new SplitterException("Split needs at least one person");
                }
                List<String> names = new ArrayList<>();
                List<BigDecimal> values = new ArrayList<>();
                BigDecimal sum = BigDecimal.ZERO;
                for (Map.Entry<String, BigDecimal> entry : p.percentages().entrySet()) {
                    String name = resolver.apply(entry.getKey());
                    if (entry.getValue().signum() <= 0) {
                        throw new SplitterException("Percentage for " + name + " must be greater than zero");
                    }
                    BigDecimal percent = entry.getValue().setScale(2);
                    names.add(name);
                    values.add(percent);
                    sum = sum.add(percent);
                }
                rejectDuplicates(names);
                if (sum.compareTo(HUNDRED) != 0) {
                    throw new SplitterException("Percentages add up to " + Money.format(sum) + ", not 100");
                }
                var resolved = new LinkedHashMap<String, BigDecimal>();
                for (int i = 0; i < names.size(); i++) {
                    resolved.put(names.get(i), values.get(i));
                }
                yield new Split.Percent(resolved);
            }
        };
    }

    private static void rejectDuplicates(List<String> names) {
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                throw new SplitterException(name + " is listed twice");
            }
        }
    }
}

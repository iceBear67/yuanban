package io.ib67.yuanban.crafting.internal.item;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * 原版 {@code StackedContents}：统计可用的物品数量，用增广路径为每个原料分配一种物品，判断能否合成以及最多能合成几份。
 * 物品的遍历顺序与原版相同，取决于 {@link Reference2IntOpenHashMap} 中对象身份散列值的顺序。
 */
@ApiStatus.Internal
public final class StackedContents<T> {
    private final Reference2IntOpenHashMap<T> amounts = new Reference2IntOpenHashMap<>();

    private boolean hasAtLeast(T item, int count) {
        return amounts.getInt(item) >= count;
    }

    private void take(T item, int amount) {
        int previous = amounts.addTo(item, -amount);
        if (previous < amount) {
            throw new IllegalStateException("Took " + amount + " items, but only had " + previous);
        }
    }

    private void put(T item, int count) {
        amounts.addTo(item, count);
    }

    public boolean tryPick(List<? extends IngredientInfo<T>> ingredients, int amount, @Nullable Output<T> output) {
        return new RecipePicker(ingredients).tryPick(amount, output);
    }

    public int tryPickAll(List<? extends IngredientInfo<T>> ingredients, int maxSize, @Nullable Output<T> output) {
        return new RecipePicker(ingredients).tryPickAll(maxSize, output);
    }

    public void account(T item, int count) {
        put(item, count);
    }

    private List<T> getUniqueAvailableIngredientItems(Iterable<? extends IngredientInfo<T>> ingredients) {
        var result = new ArrayList<T>();
        for (Reference2IntMap.Entry<T> available : Reference2IntMaps.fastIterable(amounts)) {
            if (available.getIntValue() > 0 && anyIngredientMatches(ingredients, available.getKey())) {
                result.add(available.getKey());
            }
        }
        return result;
    }

    private static <T> boolean anyIngredientMatches(Iterable<? extends IngredientInfo<T>> ingredients, T item) {
        for (var ingredient : ingredients) {
            if (ingredient.acceptsItem(item)) {
                return true;
            }
        }
        return false;
    }

    private int getResultUpperBound(List<? extends IngredientInfo<T>> ingredients) {
        int min = Integer.MAX_VALUE;
        var availableItems = Reference2IntMaps.fastIterable(amounts);
        next:
        for (var ingredient : ingredients) {
            int max = 0;
            for (Reference2IntMap.Entry<T> entry : availableItems) {
                int itemCount = entry.getIntValue();
                if (itemCount > max) {
                    if (ingredient.acceptsItem(entry.getKey())) {
                        max = itemCount;
                    }
                    if (max >= min) {
                        continue next;
                    }
                }
            }
            min = max;
            if (max == 0) {
                break;
            }
        }
        return min;
    }

    @FunctionalInterface
    public interface IngredientInfo<T> {
        boolean acceptsItem(T item);
    }

    @FunctionalInterface
    public interface Output<T> {
        void accept(T item);
    }

    /**
     * 原料与物品构成二分图，{@code data} 依次保存：访问过的原料、访问过的物品、已满足的原料、连接、当前分配。
     */
    private final class RecipePicker {
        private final List<? extends IngredientInfo<T>> ingredients;
        private final int ingredientCount;
        private final List<T> items;
        private final int itemCount;
        private final BitSet data;
        private final IntList path = new IntArrayList();

        RecipePicker(List<? extends IngredientInfo<T>> ingredients) {
            this.ingredients = ingredients;
            this.ingredientCount = ingredients.size();
            this.items = getUniqueAvailableIngredientItems(ingredients);
            this.itemCount = items.size();
            this.data = new BitSet(visitedIngredientCount() + visitedItemCount() + satisfiedCount() + connectionCount() + residualCount());
            setInitialConnections();
        }

        private void setInitialConnections() {
            for (int ingredient = 0; ingredient < ingredientCount; ingredient++) {
                var info = ingredients.get(ingredient);
                for (int item = 0; item < itemCount; item++) {
                    if (info.acceptsItem(items.get(item))) {
                        setConnection(item, ingredient);
                    }
                }
            }
        }

        boolean tryPick(int capacity, @Nullable Output<T> output) {
            if (capacity <= 0) {
                return true;
            }
            int satisfiedIngredientCount = 0;
            while (true) {
                var found = tryAssigningNewItem(capacity);
                if (found == null) {
                    boolean isValidAssignment = satisfiedIngredientCount == ingredientCount;
                    boolean hasOutput = isValidAssignment && output != null;
                    clearAllVisited();
                    clearSatisfied();
                    for (int ingredient = 0; ingredient < ingredientCount; ingredient++) {
                        for (int item = 0; item < itemCount; item++) {
                            if (isAssigned(item, ingredient)) {
                                unassign(item, ingredient);
                                put(items.get(item), capacity);
                                if (hasOutput) {
                                    output.accept(items.get(item));
                                }
                                break;
                            }
                        }
                    }
                    return isValidAssignment;
                }
                int assignedItem = found.getInt(0);
                take(items.get(assignedItem), capacity);
                int satisfiedIngredient = found.size() - 1;
                setSatisfied(found.getInt(satisfiedIngredient));
                satisfiedIngredientCount++;
                for (int i = 0; i < found.size() - 1; i++) {
                    if (isPathIndexItem(i)) {
                        assign(found.getInt(i), found.getInt(i + 1));
                    } else {
                        unassign(found.getInt(i + 1), found.getInt(i));
                    }
                }
            }
        }

        private static boolean isPathIndexItem(int index) {
            return (index & 1) == 0;
        }

        private @Nullable IntList tryAssigningNewItem(int capacity) {
            clearAllVisited();
            for (int item = 0; item < itemCount; item++) {
                if (hasAtLeast(items.get(item), capacity)) {
                    var found = findNewItemAssignmentPath(item);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        private @Nullable IntList findNewItemAssignmentPath(int startingItem) {
            path.clear();
            visitItem(startingItem);
            path.add(startingItem);
            while (!path.isEmpty()) {
                int pathLength = path.size();
                if (isPathIndexItem(pathLength - 1)) {
                    int itemToAssign = path.getInt(pathLength - 1);
                    for (int ingredient = 0; ingredient < ingredientCount; ingredient++) {
                        if (!hasVisitedIngredient(ingredient) && hasConnection(itemToAssign, ingredient) && !isAssigned(itemToAssign, ingredient)) {
                            visitIngredient(ingredient);
                            path.add(ingredient);
                            break;
                        }
                    }
                } else {
                    int lastAssignedIngredient = path.getInt(pathLength - 1);
                    if (!isSatisfied(lastAssignedIngredient)) {
                        return path;
                    }
                    for (int item = 0; item < itemCount; item++) {
                        if (!hasVisitedItem(item) && isAssigned(item, lastAssignedIngredient)) {
                            visitItem(item);
                            path.add(item);
                            break;
                        }
                    }
                }
                int newLength = path.size();
                if (newLength == pathLength) {
                    path.removeInt(newLength - 1);
                }
            }
            return null;
        }

        private int visitedIngredientOffset() {
            return 0;
        }

        private int visitedIngredientCount() {
            return ingredientCount;
        }

        private int visitedItemOffset() {
            return visitedIngredientOffset() + visitedIngredientCount();
        }

        private int visitedItemCount() {
            return itemCount;
        }

        private int satisfiedOffset() {
            return visitedItemOffset() + visitedItemCount();
        }

        private int satisfiedCount() {
            return ingredientCount;
        }

        private int connectionOffset() {
            return satisfiedOffset() + satisfiedCount();
        }

        private int connectionCount() {
            return ingredientCount * itemCount;
        }

        private int residualOffset() {
            return connectionOffset() + connectionCount();
        }

        private int residualCount() {
            return ingredientCount * itemCount;
        }

        private boolean isSatisfied(int ingredient) {
            return data.get(satisfiedOffset() + ingredient);
        }

        private void setSatisfied(int ingredient) {
            data.set(satisfiedOffset() + ingredient);
        }

        private void clearSatisfied() {
            clearRange(satisfiedOffset(), satisfiedCount());
        }

        private void setConnection(int item, int ingredient) {
            data.set(connectionOffset() + item * ingredientCount + ingredient);
        }

        private boolean hasConnection(int item, int ingredient) {
            return data.get(connectionOffset() + item * ingredientCount + ingredient);
        }

        private boolean isAssigned(int item, int ingredient) {
            return data.get(residualOffset() + item * ingredientCount + ingredient);
        }

        private void assign(int item, int ingredient) {
            data.set(residualOffset() + item * ingredientCount + ingredient);
        }

        private void unassign(int item, int ingredient) {
            data.clear(residualOffset() + item * ingredientCount + ingredient);
        }

        private void visitIngredient(int ingredient) {
            data.set(visitedIngredientOffset() + ingredient);
        }

        private boolean hasVisitedIngredient(int ingredient) {
            return data.get(visitedIngredientOffset() + ingredient);
        }

        private void visitItem(int item) {
            data.set(visitedItemOffset() + item);
        }

        private boolean hasVisitedItem(int item) {
            return data.get(visitedItemOffset() + item);
        }

        private void clearAllVisited() {
            clearRange(visitedIngredientOffset(), visitedIngredientCount());
            clearRange(visitedItemOffset(), visitedItemCount());
        }

        private void clearRange(int offset, int count) {
            data.clear(offset, offset + count);
        }

        int tryPickAll(int maxSize, @Nullable Output<T> output) {
            int min = 0;
            int max = Math.min(maxSize, getResultUpperBound(ingredients)) + 1;
            while (true) {
                int mid = (min + max) / 2;
                if (tryPick(mid, null)) {
                    if (max - min <= 1) {
                        if (mid > 0) {
                            tryPick(mid, output);
                        }
                        return mid;
                    }
                    min = mid;
                } else {
                    max = mid;
                }
            }
        }
    }
}

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Patina Pandemonium - workbench-lineage combination counter.

The script intentionally exposes two finite benchmarks instead of hiding the
fact that an unrestricted self-referential crafting history is unbounded:

1. full-tree: a complete n-ary crafting tree where every leaf has a finite
   surface identity and every internal craft may use an independently chosen
   physical workbench identity;
2. coupled: generic crafted items and workbenches recursively inherit each
   other's lineage, under an explicit generation cap;
3. recipe-graph: optional JSON recipe traversal, inspired by the *idea* of
   NotEnoughBlocks' counter but implemented independently and depth-bounded.

No UUID, wall-clock time, free-form text, or random nonce participates in any
count. Every factor is intended to correspond to a player-visible or
provenance-preserved gameplay distinction.
"""

from __future__ import annotations

import argparse
import glob
import json
import math
import os
import sys
from collections import defaultdict
from dataclasses import dataclass
from functools import lru_cache
from typing import Any, Iterable

COPPER_STATES = 8
WORKBENCH_FORMS = 9
RGB_STATES = 1 << 24
DEFAULT_INPUT_ARITY = 9
DEFAULT_WORKBENCH_RECIPE_ARITY = 4

BASE_SURFACE_STATES = COPPER_STATES * RGB_STATES
WORKBENCH_VISIBLE_STATES = COPPER_STATES * WORKBENCH_FORMS * RGB_STATES

# Reference values documented by the uploaded projects. They are comparison
# labels only and never participate in Patina's computation.
MORE_COPPER_BLOCK_LOG10 = 216 * math.log10(8)
NOT_ENOUGH_BLOCKS_V26_LOG10 = 2244.8641
NOT_ENOUGH_BLOCKS_V27_LOG10 = 3846.3678


def log10_add(left: float, right: float) -> float:
    """Return log10(10**left + 10**right) without materializing huge ints."""
    if left == -math.inf:
        return right
    if right == -math.inf:
        return left
    high, low = (left, right) if left >= right else (right, left)
    delta = low - high
    if delta < -320.0:
        return high
    return high + math.log10(1.0 + 10.0 ** delta)


def log10_sum(values: Iterable[float]) -> float:
    total = -math.inf
    for value in values:
        total = log10_add(total, value)
    return total


def scientific(log10_value: float, decimals: int = 6) -> str:
    if log10_value == -math.inf:
        return "0"
    exponent = math.floor(log10_value)
    mantissa = 10.0 ** (log10_value - exponent)
    return f"{mantissa:.{decimals}f} × 10^{exponent}"


def decimal_digits(log10_value: float) -> int:
    return 1 if log10_value <= 0.0 else math.floor(log10_value) + 1


@dataclass(frozen=True)
class TreeResult:
    depth: int
    arity: int
    leaves: int
    craft_nodes: int
    log10_value: float


def full_tree(depth: int, arity: int = DEFAULT_INPUT_ARITY,
              item_states: int = BASE_SURFACE_STATES,
              workbench_states: int = WORKBENCH_VISIBLE_STATES) -> TreeResult:
    """Finite lower-bound style tree.

    Every leaf chooses one item surface identity. Every internal node chooses
    one physical workbench visible identity. VPG makes the ordered input roots
    and the workbench root part of the result identity.
    """
    if depth < 0:
        raise ValueError("depth must be >= 0")
    if arity < 2:
        raise ValueError("arity must be >= 2")
    leaves = arity ** depth
    craft_nodes = 0 if depth == 0 else (leaves - 1) // (arity - 1)
    log_value = leaves * math.log10(item_states) + craft_nodes * math.log10(workbench_states)
    return TreeResult(depth, arity, leaves, craft_nodes, log_value)


@dataclass(frozen=True)
class CoupledResult:
    generation: int
    item_log10: float
    workbench_log10: float



def exact_full_tree_value(result: TreeResult, item_states: int = BASE_SURFACE_STATES,
                          workbench_states: int = WORKBENCH_VISIBLE_STATES) -> int:
    return pow(item_states, result.leaves) * pow(workbench_states, result.craft_nodes)


def coupled_lineage(generations: int, item_arity: int = DEFAULT_INPUT_ARITY,
                    workbench_recipe_arity: int = DEFAULT_WORKBENCH_RECIPE_ARITY,
                    item_states: int = BASE_SURFACE_STATES,
                    workbench_states: int = WORKBENCH_VISIBLE_STATES) -> list[CoupledResult]:
    """A conservative self-referential lineage recurrence.

    I_0 = item_states
    T_0 = workbench_states
    I_(g+1) = I_g^item_arity * T_g
    T_(g+1) = I_g^workbench_recipe_arity * T_g

    The second recurrence represents crafting a new workbench from four
    provenance-bearing inputs on an already provenance-bearing workbench.
    No extra post-craft recoloring factor is multiplied in, making this a
    deliberately conservative constructive benchmark.
    """
    if generations < 0:
        raise ValueError("generations must be >= 0")
    item_log = math.log10(item_states)
    table_log = math.log10(workbench_states)
    results = [CoupledResult(0, item_log, table_log)]
    for generation in range(1, generations + 1):
        previous_item = item_log
        previous_table = table_log
        item_log = item_arity * previous_item + previous_table
        table_log = workbench_recipe_arity * previous_item + previous_table
        results.append(CoupledResult(generation, item_log, table_log))
    return results


# ---------------------------------------------------------------------------
# Optional recipe graph benchmark
# ---------------------------------------------------------------------------

Ref = str | tuple[str, ...]


@dataclass(frozen=True)
class ParsedRecipe:
    result: str
    kind: str
    inputs: tuple[Ref, ...]
    inventory_compatible: bool


def normalize_ref(value: Any) -> Ref | None:
    if isinstance(value, str):
        return value
    if isinstance(value, list):
        refs = tuple(ref for entry in value if (ref := normalize_ref(entry)) is not None)
        flattened: list[str] = []
        for ref in refs:
            if isinstance(ref, tuple):
                flattened.extend(ref)
            else:
                flattened.append(ref)
        return tuple(sorted(set(flattened))) if flattened else None
    if isinstance(value, dict):
        if "item" in value:
            return str(value["item"])
        if "tag" in value:
            tag = str(value["tag"])
            return tag if tag.startswith("#") else "#" + tag
        # 26.1-style ingredient/item stack templates may use id directly.
        if "id" in value and isinstance(value["id"], str):
            return str(value["id"])
    return None


def result_id(data: dict[str, Any]) -> str | None:
    result = data.get("result")
    if isinstance(result, str):
        return result
    if isinstance(result, dict):
        value = result.get("id") or result.get("item")
        return str(value) if isinstance(value, str) else None
    return None


def parse_recipe(data: dict[str, Any]) -> ParsedRecipe | None:
    recipe_type = str(data.get("type", ""))
    result = result_id(data)
    if result is None:
        return None

    if recipe_type == "minecraft:crafting_shaped":
        key = data.get("key")
        pattern = data.get("pattern")
        if not isinstance(key, dict) or not isinstance(pattern, list) or not pattern:
            return None
        refs: list[Ref] = []
        width = 0
        height = len(pattern)
        for row in pattern:
            if not isinstance(row, str):
                continue
            width = max(width, len(row))
            for symbol in row:
                if symbol == " " or symbol not in key:
                    continue
                ref = normalize_ref(key[symbol])
                if ref is not None:
                    refs.append(ref)
        if not refs:
            return None
        return ParsedRecipe(result, "shaped", tuple(refs), width <= 2 and height <= 2)

    if recipe_type == "minecraft:crafting_shapeless":
        ingredients = data.get("ingredients")
        if not isinstance(ingredients, list):
            return None
        refs = tuple(ref for ingredient in ingredients if (ref := normalize_ref(ingredient)) is not None)
        if not refs:
            return None
        return ParsedRecipe(result, "shapeless", refs, len(refs) <= 4)

    return None


def load_recipes(root: str) -> list[ParsedRecipe]:
    parsed: list[ParsedRecipe] = []
    for path in glob.glob(os.path.join(root, "**", "*.json"), recursive=True):
        try:
            with open(path, "r", encoding="utf-8") as handle:
                data = json.load(handle)
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            continue
        entries = data if isinstance(data, list) else [data]
        for entry in entries:
            if not isinstance(entry, dict):
                continue
            recipe = parse_recipe(entry)
            if recipe is not None:
                parsed.append(recipe)
    return parsed


class RecipeGraphCounter:
    """Depth-bounded recursive recipe counter in log10 space.

    The graph counter does not claim to be a registry-exact count. It is a
    reproducible benchmark with explicit assumptions:
      * every ordinary item starts with BASE_SURFACE_STATES identities;
      * crafting tables start with WORKBENCH_VISIBLE_STATES identities;
      * a 3x3 physical craft can be performed on any workbench identity from
        the previous depth;
      * 2x2-compatible recipes count both inventory crafting and workbench
        crafting as different provenance contexts;
      * tags are conservative abstract references unless expanded externally;
      * alternatives are summed;
      * recursion is cut by an explicit depth, so cycles never imply infinity.
    """

    def __init__(self, recipes: list[ParsedRecipe], item_states: int = BASE_SURFACE_STATES,
                 workbench_states: int = WORKBENCH_VISIBLE_STATES,
                 output_surface_factor: bool = False) -> None:
        self.item_states = item_states
        self.workbench_states = workbench_states
        self.output_surface_factor = output_surface_factor
        self.by_result: dict[str, list[ParsedRecipe]] = defaultdict(list)
        self.items: set[str] = set()
        for recipe in recipes:
            self.by_result[recipe.result].append(recipe)
            self.items.add(recipe.result)
            for ref in recipe.inputs:
                if isinstance(ref, tuple):
                    self.items.update(value for value in ref if not value.startswith("#"))
                elif not ref.startswith("#"):
                    self.items.add(ref)

    def base_log(self, item: str) -> float:
        states = self.workbench_states if item == "minecraft:crafting_table" else self.item_states
        return math.log10(states)

    @lru_cache(maxsize=None)
    def forms_log(self, item: str, depth: int) -> float:
        base = self.base_log(item)
        if depth <= 0:
            return base
        recipe_logs = [self.recipe_log(recipe, depth) for recipe in self.by_result.get(item, ())]
        return log10_sum([base, *recipe_logs])

    def ref_log(self, ref: Ref, depth: int) -> float:
        if isinstance(ref, tuple):
            logs = [self.ref_log(value, depth) for value in ref]
            return log10_sum(logs)
        if ref.startswith("#"):
            # A tag stands for at least one legal item family. Treating it as a
            # single base-state node avoids silently inventing tag membership.
            return math.log10(self.item_states)
        return self.forms_log(ref, depth)

    def recipe_log(self, recipe: ParsedRecipe, depth: int) -> float:
        input_log = sum(self.ref_log(ref, depth - 1) for ref in recipe.inputs)
        table_log = self.forms_log("minecraft:crafting_table", depth - 1)
        if recipe.inventory_compatible:
            context_log = log10_add(0.0, table_log)  # inventory context + physical workbench contexts
        else:
            context_log = table_log
        output_log = self.base_log(recipe.result) if self.output_surface_factor else 0.0
        return input_log + context_log + output_log

    def total_log(self, depth: int) -> float:
        return log10_sum(self.forms_log(item, depth) for item in sorted(self.items))


def print_constants() -> None:
    print("Patina Pandemonium workbench-lineage counter")
    print("=" * 60)
    print(f"Copper-style states       : {COPPER_STATES:,}")
    print(f"Workbench forms           : {WORKBENCH_FORMS:,}")
    print(f"24-bit RGB states         : {RGB_STATES:,}")
    print(f"Base item surface states  : {BASE_SURFACE_STATES:,}")
    print(f"Workbench visible states  : {WORKBENCH_VISIBLE_STATES:,}")
    print(f"log10(base item)          : {math.log10(BASE_SURFACE_STATES):.12f}")
    print(f"log10(workbench)          : {math.log10(WORKBENCH_VISIBLE_STATES):.12f}")
    print()


def print_comparisons() -> None:
    print("Reference benchmarks")
    print("-" * 60)
    print(f"MoreCopperBlock 8^216     : ~10^{MORE_COPPER_BLOCK_LOG10:.4f}")
    print(f"NotEnoughBlocks v26       : ~10^{NOT_ENOUGH_BLOCKS_V26_LOG10:.4f}")
    print(f"NotEnoughBlocks v27       : ~10^{NOT_ENOUGH_BLOCKS_V27_LOG10:.4f}")
    print()


def print_tree(depth: int, arity: int, exact: bool = False, exact_file: str | None = None) -> None:
    result = full_tree(depth, arity)
    print("Finite full-tree benchmark")
    print("-" * 60)
    print(f"Depth                     : {result.depth}")
    print(f"Input arity               : {result.arity}")
    print(f"Leaf identities           : {result.leaves:,}")
    print(f"Physical craft nodes      : {result.craft_nodes:,}")
    print(f"log10(N)                  : {result.log10_value:.12f}")
    print(f"N                          : {scientific(result.log10_value)}")
    print(f"Decimal digits            : {decimal_digits(result.log10_value):,}")
    print(f"Beats NEB v27 by exponent : {result.log10_value - NOT_ENOUGH_BLOCKS_V27_LOG10:+.4f}")
    if exact or exact_file:
        value = exact_full_tree_value(result)
        if hasattr(sys, "set_int_max_str_digits"):
            sys.set_int_max_str_digits(0)
        text = str(value)
        if exact:
            print(f"Exact integer              : {text}")
        if exact_file:
            with open(exact_file, "w", encoding="utf-8") as handle:
                handle.write(text + "\n")
            print(f"Exact integer file         : {os.path.abspath(exact_file)}")
    print()


def print_coupled(generations: int, item_arity: int, table_arity: int) -> None:
    print("Coupled workbench-lineage benchmark")
    print("-" * 60)
    print("gen | log10(generic item identities) | log10(workbench identities)")
    for result in coupled_lineage(generations, item_arity, table_arity):
        print(f"{result.generation:>3} | {result.item_log10:>30.6f} | {result.workbench_log10:>27.6f}")
    print()


def print_recipe_graph(root: str, depth: int, output_surface_factor: bool) -> None:
    recipes = load_recipes(root)
    counter = RecipeGraphCounter(recipes, output_surface_factor=output_surface_factor)
    total = counter.total_log(depth)
    table = counter.forms_log("minecraft:crafting_table", depth)
    print("Depth-bounded recipe-graph benchmark")
    print("-" * 60)
    print(f"Recipe directory          : {os.path.abspath(root)}")
    print(f"Parsed crafting recipes   : {len(recipes):,}")
    print(f"Participating item ids    : {len(counter.items):,}")
    print(f"Depth cap                 : {depth}")
    print(f"NEB-style output factor   : {'yes' if output_surface_factor else 'no'}")
    print(f"log10(all item identities): {total:.12f}")
    print(f"Total                      : {scientific(total)}")
    print(f"log10(crafting_table)     : {table:.12f}")
    print(f"Beats NEB v27 by exponent : {total - NOT_ENOUGH_BLOCKS_V27_LOG10:+.4f}")
    print()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--depth", type=int, default=3, help="finite-tree / recipe-graph lineage depth (default: 3)")
    parser.add_argument("--arity", type=int, default=DEFAULT_INPUT_ARITY, help="inputs per full-tree craft (default: 9)")
    parser.add_argument("--generations", type=int, default=5, help="coupled-lineage generations (default: 5)")
    parser.add_argument("--workbench-recipe-arity", type=int, default=DEFAULT_WORKBENCH_RECIPE_ARITY,
                        help="inputs used to craft a workbench in the coupled model (default: 4)")
    parser.add_argument("--recipe-dir", help="optional directory of recipe JSON files")
    parser.add_argument("--exact", action="store_true", help="print the exact finite full-tree integer (can be very long)")
    parser.add_argument("--exact-file", help="write the exact finite full-tree integer to a UTF-8 text file")
    parser.add_argument("--neb-output-factor", action="store_true",
                        help="recipe-graph mode only: multiply every crafted output by its surface-state count, matching NEB's benchmark style")
    args = parser.parse_args()

    print_constants()
    print_comparisons()
    print_tree(args.depth, args.arity, args.exact, args.exact_file)
    print_coupled(args.generations, args.arity, args.workbench_recipe_arity)
    if args.recipe_dir:
        print_recipe_graph(args.recipe_dir, args.depth, args.neb_output_factor)


if __name__ == "__main__":
    main()

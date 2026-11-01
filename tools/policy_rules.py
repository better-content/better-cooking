"""Explicit semantic equivalences, not namespace/name heuristics."""
ALIASES = {
    'create:wheat_flour': 'kaleidoscope_cookery:flour',
    'create:dough': 'kaleidoscope_cookery:raw_dough',
    'farmersdelight:wheat_dough': 'kaleidoscope_cookery:raw_dough',
    'kaleidoscope_cookery:tomato': 'farmersdelight:tomato',
    'kaleidoscope_cookery:tomato_seed': 'farmersdelight:tomato_seeds',
    'kaleidoscope_cookery:lettuce': 'farmersdelight:cabbage',
    'kaleidoscope_cookery:lettuce_seed': 'farmersdelight:cabbage_seeds',
    'kaleidoscope_cookery:rice': 'farmersdelight:rice',
}
BULK_ALIASES = {key: ALIASES[key] for key in ('create:wheat_flour', 'create:dough', 'farmersdelight:wheat_dough')}
CROP_ALIASES = {key: value for key, value in ALIASES.items() if key not in BULK_ALIASES}

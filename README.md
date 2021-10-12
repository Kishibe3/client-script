# Client Script Mod

```
Chat on a server somewhere...
    <me> cscript run global_afk = true
    <me> Oops
    <a 2b2t test server player> what is cscript?
    <another 2b2t test server player> cum script
**ok not funny**
```

This is a fabric mod that use the scarpet language to program and control everything you can do on client side. [See the scarpet documentation](https://github.com/gnembon/fabric-carpet/blob/master/docs/scarpet/Documentation.md).

With this mod, you can write program to achieve some functionality other clients provided. You can easily customize everything that other clients usually can't do well.

Some of the code in this mod is same as in [carpet mod](https://github.com/gnembon/fabric-carpet), credit belongs to carpet mod.

This mod doesn't support the Minecraft API part of scarpet language because it is just a client side mod.

# cscript command

There are 2 unique apps which are special for this mod: <span style="color: red">load.sc</span> and <span style="color: red">main.sc</span>. load.sc will be loaded and executed once the Minecraft client is running. main.sc will be loaded once the Minecraft client is running and executed every tick in game. They are just like mcfunctions that have a load or tick tag when you are writing datapacks. <span style="color: red">Both should be placed in .minecraft/config/cscript folder.</span>

Below are some usage of `/cscript` command:

### `/cscript run [expression]`

Calculate the expression and print the result of final expression.

ex. /cscript run 1+1

### `/cscript globals`

Show the global variables and global functions.

### `/cscript load [app]`

Load and execute app (.sc or .scl files) globally in .minecraft/config/cscript folder. <span style="color: red">Apps should be put in .minecraft/config/cscript folder.</span>

ex. /cscript load attack.sc

### `/cscript reload`

Reload load.sc and main.sc app.

# Additional Functions

Although this mod doesn't support Minecraft API part of scarpet, it adds some functions to enrich this mod. Some functions are also imgrated from the original carpet mod, credit belongs to carpet mod.

You can see these additional functions [here]() and some useful examples [there]().

If you have some questions but can't find the answers here, maybe you can check out [the scarpet part of carpet wiki](https://github.com/gnembon/fabric-carpet/wiki/Scarpet).

# For Developers

You can follow the tutorial [here](https://fabricmc.net/wiki/tutorial:setup). Just replace the starting files, fabric-example-mod, with client-script.
Welcome to Holograms by libraryaddict.

I do plan to maintain this project, up until 1.8 is released.

When 1.8 is released, depending if their new nametag changes allows you to do holograms, I'll convert the project to that.

Creating a hologram is easy.
Hologram hologram = new Hologram(Location, Lines of text).start();

You can set the hologram relative to a entity with hologram.setFollowEntity(entity);

As long as that entity is alive, the hologram will follow at the same position from it that it was created.
You can also use setVector to make the hologram move so and so blocks per tick.
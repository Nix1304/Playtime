## **Plugin that counts the player's time on the server**

Execute this SQL script on your MySQL server

```mysql
CREATE TABLE users_online (
    name text not null,
    playtime int not null,
    server text not null
);
```
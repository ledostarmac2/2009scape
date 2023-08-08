### Cross-platform release of the single-player 2009scape experience
### For the MMO version please go to 2009scape.org and press Click Here To Play.

#### How to use this release
1. Find the big blue button that says "Clone".
2. Click the one to its left.
3. Select ZIP release.
4. Download & extract the ZIP to a directory of your liking - preferably desktop or something like that. Program Files likely won't work.
5. Execute `launch.bat` on Windows, or `launch.sh` on a *nix-based system. Enjoy the game.

Notes:
- If you change internal server configuration and things suddenly start to be wonky - that's on you. The release is meant to be enjoyed as-is and any modifications to the server configuration files are your own.

#### My internet is very slow and the gitlab download does not work
**Note: This link is not guaranteed to be updated to the latest singleplayer, it is included as a courtesy.**

Here's a 7Z-compressed archive of the single-player release as it was on 11-Jan-2023:
https://vddcore.eu/uploads/2009scape/singleplayer/2009scape-singleplayer_11-Jan-2023.7z

You can extract this with something like 7-Zip on Windows.

#### Troubleshooting

##### My character's stats are decreasing after each login
You changed your profile's XP rate to way higher than what is supported. Maximum supported XP rate is `5.0` for normal players and `10.0` for Hardcore Ironman players. There are no plans to change the maximum supported XP rates in the future. *Requests to change it will be ignored.*

##### The console log keeps repeating `Still waiting for the server to start...`
There are is currently one known thing that can cause this:
 - You have extracted the files to a path containing spaces, e.g.: `C:\Users\My Name\Documents\2009scape`. Move the directory someplace else where there's no spaces in the path.

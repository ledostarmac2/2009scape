echo "Cloning latest build..."

remote="https://gitlab.com/2009scape/2009scape"

tag=$(git ls-remote --tags --exit-code --refs "$remote" | sed -E 's/^[[:xdigit:]]+[[:space:]]+refs\/tags\/(.+)/\1/g' | sort -t'-' -k3,3n -k1,1M -k2,2n | tail -1)

echo "Selected tag: $tag"

# Clone as shallowly as possible. Remote is the last argument.
git -c advice.detachedHead=false clone --branch "$tag" --depth 1 --quiet "$remote"

if [ -e "data" ]; then
	echo "Backing up existing user data..."
	mkdir data-backup
	mv data/eco data-backup/eco
	mv data/players data-backup/players
	mv data/serverstore data-bacup/serverstore
	rm -fr data
fi

rm -fr worldprops
rm -fr managementprops
rm -fr jars

cd 2009scape

echo "Building Management-Server"
cd Management-Server
sh mvnw -DskipTests package > /dev/null
mv target/*-with-dependencies.jar ../../ms.jar
mv managementprops ../..
cd ..

echo "Building Server (This will probably take a while.)"
cd Server
sh mvnw clean > /dev/null
sh mvnw -DskipTests package > /dev/null
mv target/*-with-dependencies.jar ../../server.jar
mv data ../..
mv worldprops ../..
mv db_exports/*.sql ../../data/
cd ../..

rm -fr 2009scape

echo "Downloading Client"
wget --quiet "cdn.2009scape.org/2009scape.jar"

mkdir jars
mv 2009scape.jar jars/client.jar
mv server.jar jars
mv ms.jar jars

if [ -e "data-backup" ]; then
	echo "Restoring existing user data..."
	mv data-backup/eco data/eco
	mv data-backup/players data/players
	mv data-backup/serverstore data/serverstore
	rm -fr data-backup
fi

echo "Modifying server config for singleplayer..."
cat worldprops/default.conf | sed -e 's/dev = true/dev = false/' | sed -e 's/debug = true/debug = false/' | sed -e 's/daily_restart = true/daily_restart = false/' | sed -e 's/activity = "2009scape classic."/activity = "Singleplayer"/' | sed -e 's/bots_influence_ge_price = true/bots_influence_ge_price = false/' > worldprops/sp.conf
mv worldprops/sp.conf worldprops/default.conf

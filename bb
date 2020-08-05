#!/bin/sh

#Blog Settings
max_chars=200 #max number of characters to display in a post body before inserting a "continue reading" link, to prevent long posts filling the page. Set to 0 to disable
drafts_dir="$BLOGDIR/.drafts" #Directory for storing drafts

#Web Settings
out_dir=$(realpath "out") #the directory where our individual blog posts are saved
template_dir=$(realpath "template") #the directory where our templates are located
out_file="out.html" #the path to our index file, containing the list of blog posts
url_index="file:///home/paul/_dev/basicblog" #the URL for the index of the blog. The index file name will be appended to this. This is used in "Go Back" linkx
url_posts="file:///home/paul/_dev/basicblog/out" #the url for each invididual post. Post filenames will be appended to this. This is used for permalinks.

########################################

#remove trailing slashes from some variables
drafts_dir=$(echo $drafts_dir | sed 's:/*$::')
out_dir=$(echo $out_dir | sed 's:/*$::')
url_index=$(echo $url_index | sed 's:/*$::')
url_posts=$(echo $url_posts | sed 's:/*$::')

help()
{
    echo "bb - a basic blogging system by Paul Tunbridge <tunbridgep@gmail.com>"
    echo
    echo "Usage: bb <command>"
    echo
    echo "Commands are"
    echo "\t(n)ew - Create a new draft. Can optionally specify a filename"
    echo "\t(e)dit - Edit an existing draft. Can optionally specify a filename"
    echo "\t(d)elete - Delete an existing draft. Can optionally specify a filename"
    echo "\t(p)ublish - Publish a draft, making it appear in the list. Can optionally specify a filename"
    echo "\t(u)npublish - Unublish a draft, returning it to draft status. Can optionally specify a filename"
    echo "\t(g)enerate - Create a blog index and files for each entry. Can optionally specify a custom template dir"
    echo "\t(h)elp - Display this message"
    echo
    echo "Blog pages will be stored in the BLOGDIR environment variable"
}

filename_add_timestamp()
{
    echo $(date +"%Y-%m-%d-%I:%M-%p")__"$@"
}

filename_remove_timestamp()
{
    echo "$@" | sed 's/^.*__//'
}

filename_get_timestamp()
{
    basename "$@" | awk -F "__" '{print $1}'
}

filename_to_title()
{
    basename "$@" ".html" | sed 's/^.*__//' | tr -s '_' | tr '_' ' ' | awk '{printf("%s%s\n",toupper(substr($0,1,1)),substr($0,2))}'
}

title_to_filename()
{
    echo "$@" | tr -s ' ' | tr ' ' '_' | awk '{print tolower($0)}'
}

get_post_exists()
{
    files=$(find $BLOGDIR -name "*__${1}.html")
    [ $files = "" ] && return 1 || return 0
}

prompt_confirm() {
  while true; do
    echo -n "${1:-Continue?} [y/n]: "
    read REPLY
    case $REPLY in
      [yY]) echo ; return 0 ;;
      [nN]) echo ; return 1 ;;
      *) printf " \033[31m %s \n\033[0m" "invalid input"
    esac 
  done  
}

new()
{
    mkdir -p "$drafts_dir"

    if [ "$1" = "" ]; then
        echo -n "Enter Title (leave blank to cancel): "
        read ask
    else
        ask="$1"
    fi

    if [ ! "$ask" = "" ]; then
        filename=$(title_to_filename $ask)
        final_path="$drafts_dir/${filename}.html"
        if [ -f "$final_path" ]; then
            prompt_confirm "Post exists. Edit?" || return
        fi
        "$EDITOR" "$final_path"
        #edit "$final_path"
    fi
}

edit()
{
    file="$1.html"
    if [ ! -f "$drafts_dir/$file" ]; then
        list_and_return "$drafts_dir" "edit"
        file=$drafts_dir/$(get_file_from_index "$drafts_dir" $?)
    fi
    [ "$file" = "$drafts_dir/" ] && return
    "$EDITOR" "$file"
}

#$1 is the folder to list
#returns 0 on failure, or the number of the file on success
list_and_return()
{
    case "$(ls "$1" | wc -l )" in
        0) echo "Nothing to $2" && return 0 ;;
        1) number=1 ;;
        *) ls -rc "$1" | nl
            echo "Please select an option to $2 (leave blank to cancel): "
            read number
            if [ "$number" = "" ] || ! [ "$number" -eq "$number" ] 2> /dev/null; then
                return 0
            fi ;;
    esac
    return $number
}

#when we call list_and_return, we can use this
#to get the filename of whichever item we selected
get_file_from_index()
{
    [ "$2" -eq 0 ] && return
    echo "$(ls -rc "$1" | nl | grep -w " $2" | awk '{print $2}' )"
}

delete()
{
    file="$1.html"
    if [ ! -f "$drafts_dir/$file" ]; then
        list_and_return "$drafts_dir" "delete"
        file=$drafts_dir/$(get_file_from_index "$drafts_dir" $?)
    fi
    [ "$file" = "$drafts_dir/" ] && return
    base=$(basename $file)
    prompt_confirm "This will DELETE the draft $base. Are you sure?" && rm $file || return
}

publish()
{
    file="$drafts_dir/$1.html"
    if [ ! -f "$file" ]; then
        list_and_return "$drafts_dir" "publish"
        file=$drafts_dir/$(get_file_from_index "$drafts_dir" $?)
    fi
    [ "$file" = "$drafts_dir/" ] && return
    echo $file
    file_size_b=$(du -b "$file" | cut -f1)
    [ $file_size_b -eq 0 ] 2>/dev/null && echo "Cannot publish an empty file" && return
    base=$(basename $file)
    nicename=$(filename_to_title $base)
    new_filename=$(filename_add_timestamp "$base")
    prompt_confirm "This will PUBLISH the draft $base as $new_filename with the title '$nicename'. Are you sure?" || return
    mv "$file" "$BLOGDIR/$new_filename"

}

unpublish()
{
    file="$1.html"
    if [ ! -f "$BLOGDIR/$file" ]; then
        list_and_return "$BLOGDIR" "unpublish"
        file=$BLOGDIR/$(get_file_from_index "$BLOGDIR" $?)
    fi
    [ "$file" = "$BLOGDIR/" ] && return
    base=$(basename $file)
    prompt_confirm "This will UNPUBLISH the draft $base. Are you sure?" || return
    new_filename=$(filename_remove_timestamp "$base")
    mv "$file" "$drafts_dir/$new_filename"
}

process_file()
{
    basename=$(basename $2)
    titlecase=$(filename_to_title $2)
    timestamp=$(filename_get_timestamp $2)
    sed -i "s|@id|$basename|gI" "$1"
    sed -i "s|@index|$url_index/$out_file|gI" "$1"
    sed -i "s|@permalink|$url_posts/$basename|gI" "$1"
    sed -i "s|@title|$titlecase|gI" "$1"
    sed -i "s/@timestamp/$timestamp/" "$1"
}

generate()
{
    if [ "$1" = "" ]; then
        template_path=$template_dir
    else
        template_path=$1
    fi
    template_path=$(echo $template_path | sed 's:/*$::')

    if [ "$2" = "" ]; then
        out="$out_file"
    else
        out="$2"
    fi

    count=$(ls "$BLOGDIR" | wc -l)
    mkdir -p "$out_dir"

    [ $count -eq 0 ] && echo "No published posts" && return
    [ -f "$out" ] && rm "$out"

    echo "Processing $count posts"

    [ -f "$template_path/header_global.html" ] && cat "$template_path/header_global.html" > "$out"
    
    for f in "$BLOGDIR"/*; do
        file_size_b=$(du -b "$f" | cut -f1)
        [ $file_size_b -eq 0 ] && continue

        #generate content for index
        [ -f "$template_path/item_header_combined.html" ] && cat "$template_path/item_header_combined.html" >> "$out"
        if [ $max_chars -gt 0 ] && [ $file_size_b -gt $max_chars ] && [ ! "$3" = "no" ]; then
            head -c $max_chars "$f" >> $out
            echo "... " >> $out
            [ -f "$template_path/item_continue_reading.html" ] && cat "$template_path/continue_reading.html" >> "$out" || echo '<a href="@permalink">Continue Reading</a>' >> "$out"
        else
            cat "$f" >> "$out"
        fi
        [ -f "$template_path/item_footer_combined.html" ] && cat "$template_path/item_footer_combined.html" >> "$out"

        #generate content for individual file
        basename=$(basename $f)
        blogfile="$out_dir/$basename"
        [ -f "$template_path/item_header_permalink.html" ] && cat "$template_path/item_header_permalink.html" > "$blogfile"
        cat "$f" >> "$blogfile"
        if [ -f item_goback.html ]; then
            cat item_goback.html >> "$blogfile"
        else
            echo '<div class="goback"><a href="@index">Go Back</a></div>' >> "$blogfile"
        fi
        [ -f "$template_path/item_footer_permalink.html" ] && cat "$template_path/item_footer_permalink.html" >> "$blogfile"

        #replace our symbols
        process_file "$blogfile" "$blogfile"
        process_file "$out" "$blogfile"
    done
    
    [ -f "$template_path/footer_global.html" ] && cat "$template_path/footer_global.html" >> "$out"
}

if [ "$BLOGDIR" = "" ]; then
    echo "BLOGDIR environment variable is not set"
    exit 1
fi

if [ ! -d "$BLOGDIR" ]; then
    echo "Blog directory at $BLOGDIR doesn't exist"
    exit 1
fi

case "$1" in
    n*) new $2 ;;
    e*) edit $2 ;;
    d*) delete $2 ;;
    p*) publish $2 ;;
    u*) unpublish $2 ;;
    g*) generate $2 $3 $4 ;;
    *) help ;;
esac

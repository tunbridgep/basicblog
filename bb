#!/bin/sh

#User Variables

drafts_dir="$BLOGDIR/.drafts"

max_chars=200 #max number of characters to display in a post body before inserting a "continue reading" link, to prevent long posts filling the page. Set to 0 to disable
out_dir=$(realpath "out") #the directory where our individual blog posts are saved
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
    echo "bb - a basic blogging system"
    echo
    echo "Set the BLOGDIR environment variable"
    echo "blah blah blah"
    exit 0
}

filename_add_timestamp()
{
    echo $(date +"%Y-%m-%d")__"$@"
}

filename_remove_timestamp()
{
    echo "$@" | sed 's/^.*__//'
}

filename_get_timestamp()
{
    #basename "$@" | sed 's/.*__ //'
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

    return

    ##this will be needed later
	if [ ! "$ask" = "" ]; then
        filename=$(title_to_filename $ask)
        temp="$drafts_dir/__temp"
        touch "$temp"
        timestamp=$(stat -c %z "$temp" | cut -d\  -f1 )
        rm "$temp"
        final_path="$drafts_dir/${timestamp}__${filename}.html"
        existing_file=$

        if [ ! "$existing_file" = "" ]; then
            base=$(basename $existing_file)
            prompt_confirm "$base already exists. Edit?" && edit "$existing_file" || return
        fi
        edit "$final_path"
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
    #echo $file
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
    file="$1.html"
    if [ ! -f "$drafts_dir/$file" ]; then
        list_and_return "$drafts_dir" "publish"
        file=$drafts_dir/$(get_file_from_index "$drafts_dir" $?)
    fi
    [ "$file" = "$drafts_dir/" ] && return
    file_size_b=$(du -b "$file" | cut -f1)
    [ $file_size_b -eq 0 ] && echo "Cannot publish an empty file" && return
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
    #tempfile="__.html"
    count=$(ls "$BLOGDIR" | wc -l)
    mkdir -p "$out_dir"

    [ $count -eq 0 ] && echo "No published posts" && return

    echo "Processing $count posts"

    [ -f header_global.html ] && cat "header_global.html" > "$out_file"
    
    for f in "$BLOGDIR"/*; do
        file_size_b=$(du -b "$f" | cut -f1)
        [ $file_size_b -eq 0 ] && continue

        #generate content for index
        [ -f item_header_combined.html ] && cat "item_header_combined.html" >> "$out_file"
        echo "" >> "$out_file"
        if [ $max_chars -gt 0 ] && [ $file_size_b -gt $max_chars ]; then
            head -c $max_chars "$f" >> $out_file
            echo "... " >> $out_file
            [ -f item_continue_reading.html ] && cat "continue_reading.html" >> "$out_file" || echo '<a href="@permalink">Continue Reading</a>' >> "$out_file"
        else
            cat "$f" >> "$out_file"
        fi
        echo "" >> "$out_file"
        [ -f item_footer_combined.html ] && cat "item_footer_combined.html" >> "$out_file"

        #generate content for individual file
        basename=$(basename $f)
        blogfile="$out_dir/$basename"
        [ -f item_header_permalink.html ] && cat "item_header_permalink.html" > "$blogfile"
        cat "$f" >> "$blogfile"
        if [ -f item_goback.html ]; then
            cat item_goback.html >> "$blogfile"
        else
            echo '<div class="goback"><a href="@index">Go Back</a></div>' >> "$blogfile"
        fi
        [ -f item_footer_permalink.html ] && cat "item_footer_permalink.html" >> "$blogfile"

        #replace our symbols
        process_file "$blogfile" "$blogfile"
        process_file "$out_file" "$blogfile"
    done

    
    [ -f footer_global.html ] && cat "footer_global.html" >> "$out_file"
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
    g*) generate ;;
    *) help ;;
esac

exit 0


####################################################################################################

url_base=$(echo $url_base | sed 's:/*$::')
[ ! $url_base = "" ] && url_base=$url_base/
combined_url_base=$(echo $combined_url_base | sed 's:/*$::')
[ ! $combined_url_base = "" ] && combined_url_base=$combined_url_base/

fullpath=$(realpath "$BLOGDIR")
tempfile="__.html"

[ -f "$combined_outfile" ] && rm "$combined_outfile"
[ -d "$out_dir" ] && rm -r "$out_dir"

mkdir -p "$out_dir"

echo "Using blog directory $fullpath"
echo "Outputting blog files to $out_dir"

count=$(ls "$fullpath" | wc -l)
echo "Processing $count posts"


[ -f header_global.html ] && cat "header_global.html" > "$combined_outfile"

for f in "$fullpath"/*; do
    filename=$(basename "$f" ".html")
    outfile="$out_dir/$filename.html"

    titlecase=$(echo "$filename" | tr -s '_' | tr '_' ' ' | awk '{printf("%s%s\n",toupper(substr($0,1,1)),substr($0,2))}')
    timestamp=$(stat -c %z "$f" | cut -d\  -f1 )
    file_size_b=$(du -b "$f" | cut -f1)

    [ $file_size_b -eq 0 ] && echo "skipping empty file $filename.html..." && continue

    echo "Processing $filename.html..."

    ##FIX THIS
    ##LOTS OF DUPLICATED CODE!

    #first generate output for the combined version of the file
    [ -f item_header_combined.html ] && cat item_header_combined.html > "$tempfile"
    echo "" >> "$tempfile"
    if [ $max_chars -gt 0 ] && [ $file_size_b -gt $max_chars ]; then
        head -c $max_chars "$f" >> $tempfile
        echo " ... " >> $tempfile
        [ -f item_continue_reading.html ] && cat continue_reading.html >> "$tempfile" || echo '<a href="@permalink">Continue Reading</a>' >> "$tempfile"
    else
        cat "$f" >> "$tempfile"
    fi
    echo "" >> "$tempfile"
    [ -f item_footer_combined.html ] && cat item_footer_combined.html >> "$tempfile"
    

    sed -i "s|@id|$filename|gI" "$tempfile"
    sed -i "s|@permalink|$url_base$filename.html|gI" "$tempfile"
    sed -i "s|@title|$titlecase|gI" "$tempfile"
    sed -i "s/@timestamp/$timestamp/" "$tempfile"

    cat $tempfile >> $combined_outfile
    rm $tempfile

    #Then generate output for the "permalink" version
    [ -f item_header_permalink.html ] && cat item_header_permalink.html > "$outfile"
    echo "" >> "$outfile"
    cat "$f" >> "$outfile"
    echo "" >> "$outfile"
    [ -f item_footer_permalink.html ] && cat item_footer_permalink.html >> "$outfile"
    if [ -f item_goback.html ]; then
        cat item_goback.html >> "$outfile"
    else
        echo '<div class="goback"><a href="'$combined_url_base$combined_outfile'">Go Back</a></div>' >> "$outfile"
    fi

    sed -i "s|@id|$filename|gI" "$outfile"
    sed -i "s|@permalink|$url_base$filename.html|gI" "$outfile"
    sed -i "s|@title|$titlecase|gI" "$outfile"
    sed -i "s/@timestamp/$timestamp/" "$outfile"
done

[ -f footer_global.html ] && cat "footer_global.html" >> "$combined_outfile"

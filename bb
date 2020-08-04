#!/bin/sh

#User Variables

drafts_dir="$BLOGDIR/.drafts"

max_chars=200 #max number of characters to display in a posts body before inserting a "continue reading" link, to prevent long posts filling the page. Set to 0 to disable
url_base="file:///home/paul/_dev/basicblog/out" #this is the base for your url, like mysite.com/blog/..., where your blogs will link to. This is just the prefix, so myblog.com/blog/post1.html would be myblog.com/blog/
out_dir=$(realpath "out") #the directory where our permalink blog pages are saved
combined_outfile="out.html" #the path of our blog index. Doesn't necessarily have to be in the out dir
combined_url_base="file:///home/paul/_dev/basicblog" #the url of your blog index, such as mysite.com/blog/

########################################

#remove trailing spaces from some variables
drafts_dir=$(echo $drafts_dir | sed 's:/*$::')


help()
{
    echo "bb - a basic blogging system"
    echo
    echo "Set the BLOGDIR environment variable"
    echo "blah blah blah"
    exit 0
}

filename_to_title()
{
    no_timestamp=$(filename_no_timestamp "$@")
    echo "$@" | sed 's/^.*__//' | tr -s '_' | tr '_' ' ' | awk '{printf("%s%s\n",toupper(substr($0,1,1)),substr($0,2))}'
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
    echo -n "Enter Title (leave blank to cancel): "
    read ask
	if [ ! "$ask" = "" ]; then
        filename=$(title_to_filename $ask)
        final_path="$drafts_dir/${filename}.html"
        if [ -f "$final_path" ]; then
            prompt_confirm "Post exists. Edit?" || return
        fi
        edit "$final_path"
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
            prompt_confirm "Post exists. Edit?" && edit "$existing_file" || return
        fi
        edit "$final_path"
    fi
}

edit()
{
    "$EDITOR" "$@"
}

delete()
{
    echo
}

publish()
{
    mkdir -p "$drafts_dir"
    echo -n "Enter Title (leave blank to cancel): "
    read ask
	if [ ! "$ask" = "" ]; then
        filename=$(title_to_filename $ask)
    fi
}

unpublish()
{
    echo
}

generate()
{
    echo
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
    n*) new ;;
    e*) edit ;;
    d*) delete ;;
    p*) publish ;;
    u*) unpublish ;;
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

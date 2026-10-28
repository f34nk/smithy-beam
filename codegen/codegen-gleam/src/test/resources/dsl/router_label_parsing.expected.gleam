fn parse_labels(
  path: String,
  template: String
) -> Result(dict.Dict(String, String), Nil) {
  case match_segments(segments(path), segments(template), dict.new()) {
    Ok(labels) -> Ok(labels)
    Error(_) -> Error(Nil)
  }
}

fn segments(
  path: String
) -> List(String) {
  string.split(path, "/")
  |> list.filter(fn(seg) { seg != "" })
}

fn match_segments(
  path_segs: List(String),
  tpl_segs: List(String),
  acc: dict.Dict(String, String)
) -> Result(dict.Dict(String, String), Nil) {
  case path_segs, tpl_segs {
    [], [] -> Ok(acc)
    [seg, ..rest_path], [tpl_seg, ..rest_tpl] ->
      case label_name(tpl_seg) {
        Ok(key) -> {
          let val = case uri.percent_decode(seg) {
            Ok(decoded) -> decoded
            Error(_) -> seg
          }
          match_segments(rest_path, rest_tpl, dict.insert(acc, key, val))
        }
        Error(_) if seg == tpl_seg ->
          match_segments(rest_path, rest_tpl, acc)
        Error(_) -> Error(Nil)
      }
    _, _ -> Error(Nil)
  }
}

fn label_name(
  tpl_seg: String
) -> Result(String, Nil) {
  case string.starts_with(tpl_seg, "{") && string.ends_with(tpl_seg, "}") {
    True -> {
      let len = string.length(tpl_seg)
      Ok(string.slice(tpl_seg, at_index: 1, length: len - 2))
    }
    False -> Error(Nil)
  }
}

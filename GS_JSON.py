import json


orig_str_encode = json.encoder.encode_basestring_ascii
def new_str_encode(obj):
	if len(obj) > 1024:
		return "'%d %s'" % (len(obj), obj)
	return orig_str_encode(obj)

def GS_JSON_encode(obj):
	json.encoder.encode_basestring_ascii = new_str_encode
	ret = json.dumps(obj)
	json.encoder.encode_basestring_ascii = orig_str_encode
	return ret


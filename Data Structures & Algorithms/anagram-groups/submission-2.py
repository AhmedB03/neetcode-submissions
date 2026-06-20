class Solution:
    def groupAnagrams(self, strs: List[str]) -> List[List[str]]:
        res = {}

        for st in strs:
            re = {}
            for s in st:
                re[s] = 1 + re.get(s,0)
            if frozenset(re.items()) in res:
                res[frozenset(re.items())].append(st)
            else:
                
                res[frozenset(re.items())] = [st]
        
        return list(res.values())
            
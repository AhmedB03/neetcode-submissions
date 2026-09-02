class Solution:
    def exist(self, board: List[List[str]], word: str) -> bool:
        ROW, COL = len(board), len(board[0])
        path =  set()

        def dfs(r,c,i):
            if i == len(word):
                return True
            
            if (0 > r or r >= ROW or 0 > c or c >= COL or word[i] != board[r][c] or (r,c) in path):
                return False
            
            path.add((r,c))

            res = (dfs(r+1,c,i+1) or 
            dfs(r-1,c,i+1) or 
            dfs(r,c+1,i+1) or 
            dfs(r,c-1,i+1))
            path.remove((r,c))
            return res
        


        for r in range(ROW):
            for c in range(COL):
                if dfs(r,c,0): return True
        
        return False
            
        
            
